package com.upsi.smartbus

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.upsi.smartbus.databinding.FragmentDriverControlBinding
import com.upsi.smartbus.logic.EtaPredictor
import com.upsi.smartbus.logic.RouteRoadFetcher
import com.upsi.smartbus.model.NavStep
import com.upsi.smartbus.model.Route
import com.upsi.smartbus.model.RouteData
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class DriverControlFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentDriverControlBinding? = null
    private val binding get() = _binding!!

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { com.upsi.smartbus.data.FirestoreHelper.db }

    private val etaPredictor = EtaPredictor()
    private val handler = Handler(Looper.getMainLooper())
    private var currentStatus = "WORKING"
    private val terminalLines = ArrayDeque<String>()
    private val timeFormat    = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    // Google Maps
    private var googleMap: GoogleMap? = null
    private var driverBusMarker: Marker? = null
    private val segmentPolylines = mutableListOf<Polyline>()
    private val stopMarkers = mutableListOf<Marker>()
    private val segmentCache = mutableMapOf<String, List<LatLng>>()

    // Route state
    private var assignedBusId    = "BUS-001"
    private var assignedRouteName = "Laluan 1"
    private var routeStops       = listOf("KAB", "PT", "SS", "KA", "DKP", "KAB")

    // Current bus position — starts at KAB (first stop of Laluan 1)
    private var currentLat       = 3.722061
    private var currentLon       = 101.516966
    private var nextStopAbbr     = "PT"
    private var distanceToNextKm = 1.0
    private var nextStopIndex    = 1
    private var currentSpeed     = 0

    // Navigation steps for current route
    private var navSteps: List<NavStep> = emptyList()
    private var currentNavStepIndex = 0

    // 1-hour route cycling
    private var cycleStartTime = 0L
    private var cycleCount = 1
    private val CYCLE_DURATION_MS = 3_600_000L // 1 hour

    // D-Pad movement step sizes (in degrees ~= meters)
    // ~0.0002° ≈ 20m, ~0.001° ≈ 100m, ~0.0005° ≈ 50m
    private val STEP_SIZES = doubleArrayOf(0.0002, 0.0005, 0.001)
    private val STEP_LABELS = arrayOf("~20m", "~50m", "~100m")
    private var stepSizeIndex = 0

    // Auto-repeat for long-press
    private var isRepeating = false
    private val repeatDelay = 150L // ms between repeated moves
    private val repeatRunnable = object : Runnable {
        override fun run() {
            if (isRepeating && _binding != null) {
                currentMoveAction?.invoke()
                handler.postDelayed(this, repeatDelay)
            }
        }
    }
    private var currentMoveAction: (() -> Unit)? = null

    // Colors (Google Maps style)
    private val COLOR_ACTIVE   = Color.parseColor("#1A73E8")
    private val COLOR_DONE     = Color.parseColor("#90CAF9")
    private val COLOR_INACTIVE = Color.parseColor("#BDBDBD")

    // Periodic Firestore push (every 2 seconds while WORKING)
    private val firestorePushRunnable = object : Runnable {
        override fun run() {
            if (_binding == null) return
            pushToFirestore()
            handler.postDelayed(this, 2000)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDriverControlBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupDutyButtons()
        setupDPad()
        selectStatus("WORKING")

        val mapFrag = childFragmentManager.findFragmentById(R.id.driverMapView) as? SupportMapFragment
        mapFrag?.getMapAsync(this)

        loadDriverProfile()

        // Start periodic Firestore push
        handler.postDelayed(firestorePushRunnable, 2000)
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        map.apply {
            uiSettings.isZoomControlsEnabled = false
            uiSettings.isCompassEnabled = true
            uiSettings.isRotateGesturesEnabled = true
            uiSettings.isTiltGesturesEnabled = true
            moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(currentLat, currentLon), 17.0f))
            setPadding(0, 230, 0, 340)
        }
        drawRouteOnDriverMap()
        updateBusMarkerPosition()
        updateDriverPanel()
        buildDriverStopTimeline()
        cycleStartTime = System.currentTimeMillis()
        fetchNavigationSteps()
    }

    // ════════════════════════════════════════════════════════════════════
    // D-PAD CONTROLS — Manual bus movement with long-press repeat
    // ════════════════════════════════════════════════════════════════════

    private fun setupDPad() {
        setupRepeatButton(binding.btnMoveUp)    { moveBusTowardsTarget(forward = true) }
        setupRepeatButton(binding.btnMoveDown)  { moveBusTowardsTarget(forward = false) }
        setupRepeatButton(binding.btnMoveLeft)  { moveBusOffset(0.0, -STEP_SIZES[stepSizeIndex]) }
        setupRepeatButton(binding.btnMoveRight) { moveBusOffset(0.0, STEP_SIZES[stepSizeIndex]) }

        // Step size toggle
        binding.tvStepSize.text = STEP_LABELS[stepSizeIndex]
        binding.tvStepSize.setOnClickListener {
            stepSizeIndex = (stepSizeIndex + 1) % STEP_SIZES.size
            binding.tvStepSize.text = STEP_LABELS[stepSizeIndex]
            logTerminal("STEP SIZE >> ${STEP_LABELS[stepSizeIndex]}")
        }
    }

    @Suppress("ClickableViewAccessibility")
    private fun setupRepeatButton(view: View, action: () -> Unit) {
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isRepeating = true
                    currentMoveAction = action
                    action() // immediate first move
                    handler.postDelayed(repeatRunnable, 300) // start repeat after 300ms
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isRepeating = false
                    currentMoveAction = null
                    handler.removeCallbacks(repeatRunnable)
                    true
                }
                else -> false
            }
        }
    }

    private fun moveBusTowardsTarget(forward: Boolean) {
        val targetAbbr = routeStops.getOrNull(nextStopIndex) ?: "PT"
        val targetStop = RouteData.getStopByAbbreviation(targetAbbr)
        val step = STEP_SIZES[stepSizeIndex]

        if (targetStop != null) {
            val dLat = targetStop.latitude - currentLat
            val dLon = targetStop.longitude - currentLon
            val dist = Math.hypot(dLat, dLon)

            if (dist > 0.00001) {
                val dirLat = dLat / dist
                val dirLon = dLon / dist
                val mult = if (forward) 1.0 else -1.0
                currentLat += dirLat * step * mult
                currentLon += dirLon * step * mult
            } else {
                currentLat += if (forward) step else -step
            }
        } else {
            currentLat += if (forward) step else -step
        }

        onBusPositionChanged()
    }

    private fun moveBusOffset(dLat: Double, dLon: Double) {
        currentLat += dLat
        currentLon += dLon
        onBusPositionChanged()
    }

    private fun onBusPositionChanged() {
        currentSpeed = when (stepSizeIndex) {
            0 -> 25  // ~20m steps
            1 -> 40  // ~50m steps
            2 -> 60  // ~100m steps
            else -> 30
        }

        findNextStop()
        updateBusMarkerPosition()
        updateDriverPanel()
        drawRouteOnDriverMap()
        buildDriverStopTimeline()
        pushToFirestore()
        updateNavBanner()
    }

    // ════════════════════════════════════════════════════════════════════
    // PROFILE LOAD
    // ════════════════════════════════════════════════════════════════════

    private fun loadDriverProfile() {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            assignedBusId = "BUS-001"
            assignedRouteName = "Laluan 1"
            pushToFirestore()
            return
        }

        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                if (_binding == null) return@addOnSuccessListener
                val route = doc.getString("assignedRoute") ?: "Laluan 1"
                val bus   = doc.getString("assignedBus")  ?: "BUS-001"

                assignedBusId     = bus.ifEmpty { "BUS-001" }
                assignedRouteName = route

                val matched = RouteData.weekdayRoutes.find { it.name == route || it.shortName == route }
                if (matched != null && matched.stops.isNotEmpty()) routeStops = matched.stops

                val firstStop = RouteData.getStopByAbbreviation(routeStops.firstOrNull() ?: "KAB")
                if (firstStop != null) {
                    currentLat = firstStop.latitude
                    currentLon = firstStop.longitude
                }
                nextStopIndex = 1
                nextStopAbbr = routeStops.getOrElse(1) { routeStops.first() }

                drawRouteOnDriverMap()
                updateBusMarkerPosition()
                updateDriverPanel()
                buildDriverStopTimeline()
                pushToFirestore()
                fetchNavigationSteps()
            }
            .addOnFailureListener {
                if (_binding == null) return@addOnFailureListener
                assignedBusId = "BUS-001"
                assignedRouteName = "Laluan 1"
                pushToFirestore()
            }
    }

    // ════════════════════════════════════════════════════════════════════
    // MAP: DRAW ROUTE WITH PER-SEGMENT COLORING (road-following)
    // ════════════════════════════════════════════════════════════════════

    private fun drawRouteOnDriverMap() {
        val map = googleMap ?: return

        segmentPolylines.forEach { it.remove() }
        segmentPolylines.clear()
        stopMarkers.forEach { it.remove() }
        stopMarkers.clear()

        val curSegIdx = (nextStopIndex - 1).coerceAtLeast(0)

        // Draw stop markers
        for ((idx, abbr) in routeStops.withIndex()) {
            val stop = RouteData.getStopByAbbreviation(abbr) ?: continue
            val pos  = LatLng(stop.latitude, stop.longitude)

            val isNext  = (idx == nextStopIndex)
            val isDone  = (idx < nextStopIndex)
            val iconRes = when {
                isNext -> R.drawable.ic_map_stop_next
                idx == 0 -> R.drawable.ic_map_stop_start
                else -> R.drawable.ic_map_stop_default
            }

            val m = map.addMarker(
                MarkerOptions()
                    .position(pos)
                    .title("${stop.abbreviation} — ${stop.fullName}")
                    .snippet(when {
                        isNext -> "🎯 Next Stop"
                        isDone -> "✅ Passed"
                        idx == 0 -> "🏁 Start"
                        else -> "Upcoming"
                    })
                    .icon(vectorToBitmap(iconRes))
                    .anchor(0.5f, 0.5f)
                    .zIndex(if (isNext) 4f else 2f)
            )
            if (m != null) stopMarkers.add(m)
        }

        // Draw per-segment polylines (road-following via OSRM)
        for (i in 0 until routeStops.size - 1) {
            val fromStop = RouteData.getStopByAbbreviation(routeStops[i]) ?: continue
            val toStop   = RouteData.getStopByAbbreviation(routeStops[i + 1]) ?: continue
            val fromPt   = LatLng(fromStop.latitude, fromStop.longitude)
            val toPt     = LatLng(toStop.latitude, toStop.longitude)

            val isActive = (i == curSegIdx)
            val isDone   = (i < curSegIdx)
            val segKey   = "${routeStops[i]}_${routeStops[i+1]}"

            val cached = segmentCache[segKey]
            if (cached != null && cached.isNotEmpty()) {
                val poly = buildSegmentPoly(map, cached, isActive, isDone)
                segmentPolylines.add(poly)
            } else {
                val fallback = listOf(fromPt, toPt)
                val poly = buildSegmentPoly(map, fallback, isActive, isDone)
                segmentPolylines.add(poly)

                RouteRoadFetcher.fetchRoadPoints(fallback) { road ->
                    handler.post {
                        if (_binding == null) return@post
                        if (road.size > 2) {
                            segmentCache[segKey] = road
                            drawRouteOnDriverMap()
                        }
                    }
                }
            }
        }
    }

    private fun buildSegmentPoly(map: GoogleMap, pts: List<LatLng>, active: Boolean, done: Boolean): Polyline {
        return when {
            active -> map.addPolyline(PolylineOptions().addAll(pts)
                .color(COLOR_ACTIVE).width(18f).zIndex(5f).geodesic(true)
                .jointType(JointType.ROUND).startCap(RoundCap()).endCap(RoundCap()))
            done   -> map.addPolyline(PolylineOptions().addAll(pts)
                .color(COLOR_DONE).width(10f).zIndex(3f).geodesic(true)
                .jointType(JointType.ROUND).startCap(RoundCap()).endCap(RoundCap()))
            else   -> map.addPolyline(PolylineOptions().addAll(pts)
                .color(COLOR_INACTIVE).width(10f).zIndex(2f).geodesic(true)
                .pattern(listOf(Dash(25f), Gap(12f)))
                .jointType(JointType.ROUND).startCap(RoundCap()).endCap(RoundCap()))
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // BUS MARKER
    // ════════════════════════════════════════════════════════════════════

    private fun updateBusMarkerPosition() {
        val map = googleMap ?: return
        val pos = LatLng(currentLat, currentLon)

        if (driverBusMarker == null) {
            driverBusMarker = map.addMarker(
                MarkerOptions()
                    .position(pos)
                    .icon(vectorToBitmap(R.drawable.ic_map_bus_marker))
                    .anchor(0.5f, 0.5f)
                    .zIndex(10f)
            )
        } else {
            driverBusMarker?.position = pos
        }
        driverBusMarker?.title = "$assignedBusId — $assignedRouteName"
        driverBusMarker?.snippet = "Next: $nextStopAbbr | ${currentSpeed} km/h"

        // Follow camera smoothly (Google Maps navigation style: high zoom 18.5f + 45° tilt)
        map.animateCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder()
                    .target(pos)
                    .zoom(18.5f)
                    .tilt(45f)
                    .build()
            ), 500, null
        )
    }

    // ════════════════════════════════════════════════════════════════════
    // FIND NEAREST NEXT STOP (always look forward in the route)
    // ════════════════════════════════════════════════════════════════════

    private fun findNextStop() {
        if (routeStops.isEmpty()) return

        // Ensure nextStopIndex is valid
        nextStopIndex = nextStopIndex.coerceIn(0, routeStops.size - 1)
        val currentTargetAbbr = routeStops[nextStopIndex]
        val targetStop = RouteData.getStopByAbbreviation(currentTargetAbbr) ?: return

        // Distance from current bus position to the target next stop
        val distToTarget = etaPredictor.calculateDistance(currentLat, currentLon, targetStop.latitude, targetStop.longitude)
        distanceToNextKm = (distToTarget * 10.0).roundToInt() / 10.0
        nextStopAbbr = currentTargetAbbr

        // If bus is within 30m of the target next stop
        if (distToTarget < 0.03) {
            logTerminal("ARRIVED >> $currentTargetAbbr")

            if (nextStopIndex >= routeStops.size - 1) {
                logTerminal("🏁 ROUTE COMPLETE — Auto-restarting...")
                handler.postDelayed({ restartRouteCycle() }, 2000)
            } else {
                nextStopIndex++
                nextStopAbbr = routeStops[nextStopIndex]
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // DRIVER PANEL UI
    // ════════════════════════════════════════════════════════════════════

    private fun updateDriverPanel() {
        val etaMins = if (currentSpeed > 0) ((distanceToNextKm / currentSpeed) * 60).toInt().coerceAtLeast(1) else 1

        val clockFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val arrivalTimeClock = clockFormat.format(Date(System.currentTimeMillis() + etaMins * 60 * 1000L))

        binding.tvSpeed.text     = currentSpeed.toString()
        binding.tvEtaDriver.text = arrivalTimeClock
        binding.tvDistDriver.text = if (distanceToNextKm < 1.0 && distanceToNextKm > 0) {
            "${(distanceToNextKm * 1000).toInt()} m (${etaMins}m)"
        } else {
            "%.1f km (${etaMins}m)".format(distanceToNextKm)
        }
    }

    private fun logTerminal(msg: String) {
        val ts = timeFormat.format(Date())
        android.util.Log.d("DriverControl", "[$ts] $msg")
    }

    private fun buildDriverStopTimeline() {
        val container = binding.llDriverStopTimeline
        container.removeAllViews()

        val ctx = requireContext()
        val dp  = { n: Int -> TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, n.toFloat(), resources.displayMetrics).toInt() }

        for ((idx, abbr) in routeStops.withIndex()) {
            val isPassed = idx < nextStopIndex
            val isNext   = idx == nextStopIndex
            val isActive = isNext

            val stopLayout = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity     = Gravity.CENTER
            }

            val dotSize = if (isNext) dp(12) else dp(8)
            val dot = View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(dotSize, dotSize).also { it.gravity = Gravity.CENTER_HORIZONTAL }
                background = when {
                    isNext   -> ContextCompat.getDrawable(ctx, R.drawable.ic_map_stop_next)
                    isPassed -> createCircle(COLOR_DONE)
                    isActive -> createCircle(COLOR_ACTIVE)
                    else     -> createCircle(COLOR_INACTIVE)
                }
            }

            val label = TextView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.topMargin = dp(3); it.gravity = Gravity.CENTER_HORIZONTAL }
                text      = abbr
                textSize  = 9f
                gravity   = Gravity.CENTER
                typeface  = if (isNext) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setTextColor(when {
                    isNext   -> COLOR_ACTIVE
                    isPassed -> COLOR_DONE
                    else     -> COLOR_INACTIVE
                })
            }

            stopLayout.addView(dot)
            stopLayout.addView(label)

            val wrapper = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity     = Gravity.CENTER_VERTICAL
                setPadding(dp(2), 0, dp(2), 0)
            }
            wrapper.addView(stopLayout)
            container.addView(wrapper)

            if (idx < routeStops.size - 1) {
                val isArrowActive = (idx == nextStopIndex - 1)
                val isArrowDone   = (idx < nextStopIndex - 1)
                val arrow = TextView(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, dp(20)
                    ).also { it.gravity = Gravity.CENTER_VERTICAL }
                    text      = " ➜ "
                    textSize  = if (isArrowActive) 16f else 12f
                    setTextColor(when { isArrowActive -> COLOR_ACTIVE; isArrowDone -> COLOR_DONE; else -> COLOR_INACTIVE })
                    typeface = if (isArrowActive) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                }
                container.addView(arrow)
            }
        }
    }

    private fun createCircle(color: Int): android.graphics.drawable.ShapeDrawable {
        return android.graphics.drawable.ShapeDrawable(android.graphics.drawable.shapes.OvalShape()).apply {
            paint.color = color
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // DUTY STATUS
    // ════════════════════════════════════════════════════════════════════

    private fun setupDutyButtons() {
        binding.btnWorking.setOnClickListener { selectStatus("WORKING") }
        binding.btnResting.setOnClickListener { selectStatus("RESTING") }
        binding.btnOffDuty.setOnClickListener { selectStatus("OFF_DUTY") }
    }

    private fun selectStatus(status: String) {
        currentStatus = status

        val inactive = R.drawable.bg_card
        binding.btnWorking.apply { setBackgroundResource(inactive); setTextColor(resources.getColor(R.color.text_secondary, null)); typeface = Typeface.DEFAULT }
        binding.btnResting.apply { setBackgroundResource(inactive); setTextColor(resources.getColor(R.color.text_secondary, null)); typeface = Typeface.DEFAULT }
        binding.btnOffDuty.apply { setBackgroundResource(inactive); setTextColor(resources.getColor(R.color.text_secondary, null)); typeface = Typeface.DEFAULT }

        val white = resources.getColor(R.color.text_white, null)
        when (status) {
            "WORKING"  -> binding.btnWorking.apply { setBackgroundResource(R.drawable.bg_status_moving);  setTextColor(white); typeface = Typeface.DEFAULT_BOLD }
            "RESTING"  -> binding.btnResting.apply { setBackgroundResource(R.drawable.bg_status_resting); setTextColor(white); typeface = Typeface.DEFAULT_BOLD }
            "OFF_DUTY" -> binding.btnOffDuty.apply { setBackgroundResource(R.drawable.bg_status_offline); setTextColor(white); typeface = Typeface.DEFAULT_BOLD }
        }

        currentSpeed = if (status == "WORKING") 0 else 0
        logTerminal("STATUS >> $status")

        if (assignedBusId.isNotEmpty()) pushToFirestore()
    }

    // ════════════════════════════════════════════════════════════════════
    // FIRESTORE PUSH — Real-time location sharing
    // ════════════════════════════════════════════════════════════════════

    private fun pushToFirestore() {
        val firestoreStatus = when (currentStatus) {
            "WORKING" -> "Working"; "RESTING" -> "Resting"; else -> "IDLE"
        }
        val driver = RouteData.getDriverForRoute(assignedRouteName)
        val targetBusId = assignedBusId.ifEmpty { driver.busId }

        val busData = hashMapOf(
            "id"             to targetBusId,
            "name"           to assignedRouteName,
            "routeName"      to assignedRouteName,
            "driverName"     to driver.driverName,
            "driverEmail"    to driver.email,
            "plateNumber"    to driver.plateNumber,
            "licensePlate"   to driver.plateNumber,
            "routeStops"     to routeStops,
            "startStop"      to (routeStops.firstOrNull() ?: "KAB"),
            "nextStop"       to nextStopAbbr,
            "distanceToNext" to distanceToNextKm,
            "location"       to com.google.firebase.firestore.GeoPoint(currentLat, currentLon),
            "speed"          to currentSpeed.toDouble(),
            "status"         to firestoreStatus,
            "passengerCount" to 12,
            "capacity"       to 40,
            "lastUpdated"    to System.currentTimeMillis()
        )

        // Write to current assigned bus document ID
        db.collection("buses").document(targetBusId).set(busData)
            .addOnSuccessListener {
                logTerminal("ONLINE SYNC OK: $targetBusId ($assignedRouteName)")
            }
            .addOnFailureListener { err ->
                val errMsg = err.localizedMessage ?: "Unknown Firestore error"
                logTerminal("ERR: $errMsg")
            }

        // Also write to BUS-001 for legacy default listeners
        if (targetBusId != "BUS-001") {
            db.collection("buses").document("BUS-001").set(busData.apply { put("id", "BUS-001") })
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // NAVIGATION & ROUTE CYCLING
    // ════════════════════════════════════════════════════════════════════

    private fun fetchNavigationSteps() {
        val waypoints = routeStops.mapNotNull { abbr ->
            RouteData.getStopByAbbreviation(abbr)?.let { LatLng(it.latitude, it.longitude) }
        }
        if (waypoints.size < 2) return

        RouteRoadFetcher.fetchNavSteps(waypoints, routeStops) { steps ->
            handler.post {
                if (_binding == null) return@post
                navSteps = steps
                currentNavStepIndex = 0
                if (steps.isNotEmpty()) {
                    binding.navBanner.visibility = View.VISIBLE
                    updateNavBanner()
                }
            }
        }
    }

    private fun updateNavBanner() {
        if (_binding == null) return
        binding.navBanner.visibility = View.VISIBLE

        // 1. Target stop
        val targetAbbr = nextStopAbbr.ifEmpty { routeStops.getOrNull(nextStopIndex) ?: "PT" }
        val targetStop = RouteData.getStopByAbbreviation(targetAbbr)
        val targetFullName = targetStop?.fullName ?: targetAbbr

        // 2. UNIFIED DISTANCE — Strictly synchronized with bottom panel!
        val currentDistKm = distanceToNextKm
        binding.tvNavDistance.text = if (currentDistKm < 1.0) {
            "${(currentDistKm * 1000).toInt()} m"
        } else {
            "%.1f km".format(currentDistKm)
        }

        // 3. Compute relative angle to determine exact turn direction
        val targetLat = targetStop?.latitude ?: currentLat
        val targetLon = targetStop?.longitude ?: currentLon

        val relativeAngle = calculateRelativeBearing(currentLat, currentLon, targetLat, targetLon)

        val (iconRes, instruction) = when {
            currentDistKm < 0.04 -> {
                Pair(R.drawable.ic_nav_arrive, "Tiba di $targetFullName")
            }
            relativeAngle in -25.0..25.0 -> {
                Pair(R.drawable.ic_nav_straight, "Terus Lurus ke $targetFullName")
            }
            relativeAngle in 25.0..135.0 -> {
                Pair(R.drawable.ic_nav_turn_right, "Belok Kanan ke $targetFullName")
            }
            relativeAngle in -135.0..-25.0 -> {
                Pair(R.drawable.ic_nav_turn_left, "Belok Kiri ke $targetFullName")
            }
            else -> {
                Pair(R.drawable.ic_nav_uturn, "Pusing Balik ke $targetFullName")
            }
        }

        binding.ivNavTurnIcon.setImageResource(iconRes)
        binding.tvNavInstruction.text = instruction

        // Next stop info
        binding.tvNavNextStop.text = targetAbbr
        val etaMins = if (currentSpeed > 0) ((distanceToNextKm / currentSpeed) * 60).toInt().coerceAtLeast(1) else 1
        binding.tvNavEta.text = "~${etaMins} min"

        updateCycleTimer()
    }

    private fun calculateRelativeBearing(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): Double {
        val dLon = Math.toRadians(toLon - fromLon)
        val lat1 = Math.toRadians(fromLat)
        val lat2 = Math.toRadians(toLat)

        val y = Math.sin(dLon) * Math.cos(lat2)
        val x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon)
        var brng = Math.toDegrees(Math.atan2(y, x))
        brng = (brng + 360) % 360
        if (brng > 180) brng -= 360
        return brng
    }

    private fun updateCycleTimer() {
        if (cycleStartTime == 0L) cycleStartTime = System.currentTimeMillis()

        val elapsed = System.currentTimeMillis() - cycleStartTime
        val remaining = CYCLE_DURATION_MS - elapsed

        if (remaining >= 0) {
            val remainingMins = (remaining / 60000).toInt()
            binding.tvCycleTimer.text = "⏱ Pusingan $cycleCount — $remainingMins min lagi"
            binding.tvCycleTimer.setTextColor(Color.parseColor("#99FFFFFF"))
        } else {
            val overdueMins = ((elapsed - CYCLE_DURATION_MS) / 60000).toInt().coerceAtLeast(1)
            binding.tvCycleTimer.text = "⚠️ LEWAT $overdueMins MIN (Overdue)"
            binding.tvCycleTimer.setTextColor(Color.parseColor("#FF5252")) // Bright red warning text
        }
    }

    private fun restartRouteCycle() {
        cycleCount++
        cycleStartTime = System.currentTimeMillis()
        
        // Reset to first stop
        val firstStop = RouteData.getStopByAbbreviation(routeStops.firstOrNull() ?: "KAB")
        if (firstStop != null) {
            currentLat = firstStop.latitude
            currentLon = firstStop.longitude
        }
        nextStopIndex = 1
        nextStopAbbr = routeStops.getOrElse(1) { routeStops.first() }
        currentNavStepIndex = 0
        
        logTerminal("🔄 ROUTE CYCLE $cycleCount STARTED")
        
        updateBusMarkerPosition()
        drawRouteOnDriverMap()
        updateDriverPanel()
        buildDriverStopTimeline()
        updateNavBanner()
        pushToFirestore()
    }

    // ════════════════════════════════════════════════════════════════════
    // HELPERS
    // ════════════════════════════════════════════════════════════════════

    private fun vectorToBitmap(res: Int): BitmapDescriptor {
        val d  = ContextCompat.getDrawable(requireContext(), res)!!
        val bm = Bitmap.createBitmap(d.intrinsicWidth.coerceAtLeast(1), d.intrinsicHeight.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val cv = Canvas(bm); d.setBounds(0, 0, cv.width, cv.height); d.draw(cv)
        return BitmapDescriptorFactory.fromBitmap(bm)
    }

    override fun onDestroyView() {
        handler.removeCallbacks(firestorePushRunnable)
        handler.removeCallbacks(repeatRunnable)
        isRepeating = false
        if (assignedBusId.isNotEmpty()) {
            db.collection("buses").document(assignedBusId)
                .update("status", "IDLE", "speed", 0.0, "lastUpdated", System.currentTimeMillis())
        }
        _binding = null
        super.onDestroyView()
    }
}
