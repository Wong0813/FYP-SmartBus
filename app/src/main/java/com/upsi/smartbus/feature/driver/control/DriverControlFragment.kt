package com.upsi.smartbus.feature.driver.control

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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
import com.upsi.smartbus.R
import com.upsi.smartbus.core.data.FirestoreHelper
import com.upsi.smartbus.core.data.RouteRepository
import com.upsi.smartbus.core.model.NavStep
import com.upsi.smartbus.core.model.Route
import com.upsi.smartbus.core.model.RouteData
import com.upsi.smartbus.core.util.BusEtaCalculator
import com.upsi.smartbus.core.util.BusSpeedCalculator
import com.upsi.smartbus.core.util.EtaPredictor
import com.upsi.smartbus.core.util.NavigationMathHelper
import com.upsi.smartbus.core.util.RouteRoadFetcher
import com.upsi.smartbus.core.util.RouteSegmentHelper
import com.google.firebase.firestore.GeoPoint
import com.upsi.smartbus.core.model.Bus
import com.upsi.smartbus.core.util.MapMarkerHelper
import com.upsi.smartbus.databinding.FragmentDriverControlBinding
import com.upsi.smartbus.feature.driver.DriverActivity
import androidx.core.graphics.ColorUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class DriverControlFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentDriverControlBinding? = null
    private val binding get() = _binding!!

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirestoreHelper.db }
    private val etaPredictor by lazy { EtaPredictor() }
    private val handler = Handler(Looper.getMainLooper())
    private val terminalLines = ArrayDeque<String>()
    private val timeFormat    = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    // Google Maps
    private var googleMap: GoogleMap? = null
    private var driverBusMarker: Marker? = null
    private val segmentPolylines = mutableListOf<Polyline>()
    private val stopMarkers = mutableListOf<Marker>()
    private val segmentCache = mutableMapOf<String, List<LatLng>>()

    // Driver & Route state
    private var driverName       = ""
    private var driverEmail      = ""
    private var assignedBusId    = ""
    private var plateNumber      = ""
    private var assignedRouteName = "Laluan 1"
    private var routeStops       = listOf("KAB", "PT", "SS", "KA", "DKP", "KAB")
    private var currentStatus    = "WORKING"

    // Simulation GPS state
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

    // Exact glowing color palette strictly unified with Admin Live Map
    private val TECH_ROUTE_COLORS = listOf(
        Color.parseColor("#00E5FF"), // 0: Cyber Cyan (Laluan 1)
        Color.parseColor("#00E676"), // 1: Neon Green (Laluan 2)
        Color.parseColor("#FF9100"), // 2: Amber Blaze (Laluan 3)
        Color.parseColor("#E040FB"), // 3: Neon Magenta (Laluan 4)
        Color.parseColor("#2979FF"), // 4: Electric Blue (Laluan 5)
        Color.parseColor("#FFD600"), // 5: Cyber Gold (Laluan 6)
        Color.parseColor("#FF5252")  // 6: Laser Coral
    )
    private val COLOR_RESTING = Color.parseColor("#FF9800")
    private val COLOR_OFFLINE = Color.parseColor("#78909C")

    private fun getActiveRouteColor(): Int {
        val index = when {
            assignedRouteName.contains("1") -> 0
            assignedRouteName.contains("2") -> 1
            assignedRouteName.contains("3") -> 2
            assignedRouteName.contains("4") -> 3
            assignedRouteName.contains("5") -> 4
            assignedRouteName.contains("6") -> 5
            else -> 0
        }
        return TECH_ROUTE_COLORS[index % TECH_ROUTE_COLORS.size]
    }

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
        binding.btnMenuInNav.setOnClickListener {
            (activity as? DriverActivity)?.openDrawer()
        }
        setupDutyButtons()
        setupDPad()
        selectStatus("WORKING")
        updateNavBanner()

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
        currentSpeed = BusSpeedCalculator.determineSimulatedSpeed(stepSizeIndex, currentStatus)

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
        val user = auth.currentUser
        val uid = user?.uid
        val email = user?.email?.lowercase() ?: ""

        if (uid == null) {
            applyFallbackByEmail(email)
            return
        }

        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                if (_binding == null) return@addOnSuccessListener
                if (doc.exists() && doc.getString("assignedRoute") != null) {
                    applyDriverProfile(
                        route = doc.getString("assignedRoute").orEmpty(),
                        bus = doc.getString("assignedBus").orEmpty(),
                        name = doc.getString("name") ?: "Driver",
                        plate = doc.getString("plateNumber").orEmpty(),
                        email = doc.getString("email") ?: email
                    )
                } else {
                    // Try lookup by email
                    lookupDriverByEmail(email)
                }
            }
            .addOnFailureListener {
                lookupDriverByEmail(email)
            }
    }

    private fun lookupDriverByEmail(email: String) {
        if (email.isNotEmpty()) {
            db.collection("users").whereEqualTo("email", email).get()
                .addOnSuccessListener { snap ->
                    if (_binding == null) return@addOnSuccessListener
                    if (!snap.isEmpty) {
                        val doc = snap.documents.first()
                        applyDriverProfile(
                            route = doc.getString("assignedRoute").orEmpty(),
                            bus = doc.getString("assignedBus").orEmpty(),
                            name = doc.getString("name") ?: "Driver",
                            plate = doc.getString("plateNumber").orEmpty(),
                            email = email
                        )
                    } else {
                        applyFallbackByEmail(email)
                    }
                }
                .addOnFailureListener {
                    applyFallbackByEmail(email)
                }
        } else {
            applyFallbackByEmail(email)
        }
    }

    private fun applyFallbackByEmail(email: String) {
        val prefs = requireContext().getSharedPreferences("driver_cached_profile", android.content.Context.MODE_PRIVATE)
        val cachedName = prefs.getString("name_$email", null)
        val cachedRoute = prefs.getString("route_$email", null)
        val cachedBus = prefs.getString("bus_$email", null)
        val cachedPlate = prefs.getString("plate_$email", null)

        if (!cachedName.isNullOrEmpty() && !cachedRoute.isNullOrEmpty()) {
            // Priority 1: Use last known modified data from Firestore
            applyDriverProfile(
                route = cachedRoute,
                bus = cachedBus.orEmpty(),
                name = cachedName,
                plate = cachedPlate.orEmpty(),
                email = email
            )
            return
        }

        // Priority 2: Only on brand-new first install without internet, use template
        val numMatch = Regex("driver(\\d+)@").find(email)?.groupValues?.get(1)?.toIntOrNull()
        val defaultAccount = when (numMatch) {
            19 -> RouteData.defaultDrivers.find { it.routeName.contains("KAB", true) }
            20 -> RouteData.defaultDrivers.find { it.routeName.contains("KUO", true) }
            in 1..18 -> RouteData.defaultDrivers.find { it.routeName.equals("Laluan $numMatch", true) }
            else -> RouteData.defaultDrivers.first()
        } ?: RouteData.defaultDrivers.first()

        applyDriverProfile(
            route = defaultAccount.routeName,
            bus = defaultAccount.busId,
            name = defaultAccount.driverName,
            plate = defaultAccount.plateNumber,
            email = email.ifEmpty { defaultAccount.email }
        )
    }

    private fun applyDriverProfile(route: String, bus: String, name: String, plate: String, email: String) {
        // Persist to local prefs so offline fallback always has the latest modified Firestore data
        if (email.isNotEmpty() && isAdded) {
            try {
                val prefs = requireContext().getSharedPreferences("driver_cached_profile", android.content.Context.MODE_PRIVATE)
                prefs.edit()
                    .putString("name_$email", name)
                    .putString("route_$email", route)
                    .putString("bus_$email", bus)
                    .putString("plate_$email", plate)
                    .apply()
            } catch (_: Exception) {}
        }

        assignedBusId     = bus.ifEmpty { "BUS-001" }
        assignedRouteName = route.ifEmpty { "Laluan 1" }
        driverName        = name
        driverEmail       = email
        plateNumber       = plate

        val matched = RouteRepository.findRoute(assignedRouteName)
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

    // ════════════════════════════════════════════════════════════════════
    // MAP: DRAW ROUTE WITH PER-SEGMENT COLORING (road-following)
    // ════════════════════════════════════════════════════════════════════

    // Route rendering state
    private var activePolyline: Polyline? = null
    private var nextStopMarker: Marker? = null
    private var currentRenderToken = 0L

    private fun drawRouteOnDriverMap() {
        val gMap = googleMap ?: return
        val color = getActiveRouteColor()
        val busPos = LatLng(currentLat, currentLon)
        val targetAbbr = nextStopAbbr.ifEmpty { routeStops.getOrNull(nextStopIndex) ?: "PT" }
        val nextStop = RouteData.getStopByAbbreviation(targetAbbr) ?: return
        val nextStopPos = LatLng(nextStop.latitude, nextStop.longitude)

        currentRenderToken++
        val token = currentRenderToken

        fun applyRoadPoints(roadPts: List<LatLng>) {
            val poly = activePolyline
            if (poly != null) {
                poly.color = color
                poly.points = roadPts
            } else {
                activePolyline = gMap.addPolyline(
                    PolylineOptions()
                        .addAll(roadPts)
                        .color(color)
                        .width(14f)
                        .geodesic(true)
                        .jointType(JointType.ROUND)
                        .startCap(RoundCap())
                        .endCap(RoundCap())
                        .zIndex(5f)
                )
            }
        }

        // 1. Check if cached road points exist for instant display (guarantees zero straight-line jump)
        val cachedRoad = RouteSegmentHelper.getCachedRoad(busPos, targetAbbr)
        if (cachedRoad != null && cachedRoad.size >= 2) {
            applyRoadPoints(cachedRoad)
        }

        // 2. Query true street road engine with token protection
        RouteSegmentHelper.dynamicallyRouteBusToNextStop(
            busId = assignedBusId,
            busPos = busPos,
            nextStopAbbr = targetAbbr,
            requestToken = token,
            onRoadPointsReady = { respToken, roadPts ->
                if (_binding == null || respToken != currentRenderToken) return@dynamicallyRouteBusToNextStop
                applyRoadPoints(roadPts)
            }
        )

        // 3. Update target next stop dot (clean matching ring strictly unified with Admin Live Map)
        val existingStop = nextStopMarker
        if (existingStop != null) {
            existingStop.position = nextStopPos
            existingStop.title = "${nextStop.abbreviation} — ${nextStop.fullName}"
            existingStop.setIcon(createNextStopMarker(requireContext(), color))
        } else {
            nextStopMarker = gMap.addMarker(
                MarkerOptions()
                    .position(nextStopPos)
                    .title("${nextStop.abbreviation} — ${nextStop.fullName}")
                    .snippet("🎯 Next Destination Stop")
                    .icon(createNextStopMarker(requireContext(), color))
                    .anchor(0.5f, 0.5f)
                    .zIndex(6f)
            )
        }
    }

    /**
     * Creates a clean matching next stop dot (Strictly copied from Admin Live Map)
     */
    private fun createNextStopMarker(ctx: Context, color: Int): BitmapDescriptor {
        val density = ctx.resources.displayMetrics.density
        val sizePx = (16 * density).toInt()
        val bm = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bm)
        val center = sizePx / 2f

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center, center, center - (1 * density), bgPaint)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = 2.5f * density
        }
        canvas.drawCircle(center, center, center - (1 * density), borderPaint)

        val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center, center, center - (4.5f * density), innerPaint)

        return BitmapDescriptorFactory.fromBitmap(bm)
    }

    // ════════════════════════════════════════════════════════════════════
    // BUS MARKER — Composite vehicle marker with floating info card
    // ════════════════════════════════════════════════════════════════════

    private fun updateBusMarkerPosition() {
        val map = googleMap ?: return
        val pos = LatLng(currentLat, currentLon)

        val tempBus = Bus(
            id = assignedBusId,
            name = assignedRouteName,
            routeName = assignedRouteName,
            plateNumber = plateNumber,
            licensePlate = plateNumber,
            routeStops = routeStops,
            startStop = routeStops.firstOrNull().orEmpty(),
            location = GeoPoint(currentLat, currentLon),
            speed = currentSpeed.toDouble(),
            nextStop = nextStopAbbr,
            distanceToNext = distanceToNextKm,
            status = currentStatus,
            lastUpdated = System.currentTimeMillis()
        )

        // Exact match with Admin Live Map color scheme
        val color = when {
            currentStatus.equals("resting", true) -> COLOR_RESTING
            currentStatus.equals("working", true) -> getActiveRouteColor()
            else -> COLOR_OFFLINE
        }

        val markerResult = MapMarkerHelper.createBusMarkerWithInfoCard(
            requireContext(), tempBus, color, true
        )

        if (driverBusMarker == null) {
            driverBusMarker = map.addMarker(
                MarkerOptions()
                    .position(pos)
                    .icon(markerResult.descriptor)
                    .anchor(markerResult.anchorX, markerResult.anchorY)
                    .zIndex(10f)
            )
        } else {
            driverBusMarker?.position = pos
            driverBusMarker?.setIcon(markerResult.descriptor)
            driverBusMarker?.setAnchor(markerResult.anchorX, markerResult.anchorY)
        }

        // Clean 2D tracking camera strictly matching Admin Live Map & Student Map
        map.animateCamera(
            CameraUpdateFactory.newLatLngZoom(pos, 17.0f),
            500, null
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
        val distToTarget = EtaPredictor.calculateDistanceKm(currentLat, currentLon, targetStop.latitude, targetStop.longitude)
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
    // DRIVER PANEL UI (Speed, Arrive Clock, Remaining Dist, AI Predict)
    // ════════════════════════════════════════════════════════════════════

    private fun updateDriverPanel() {
        val etaMins = BusEtaCalculator.calculateStandardEtaMinutes(distanceToNextKm, currentSpeed.toDouble())
        val arrivalTimeClock = BusEtaCalculator.calculateArrivalClock(etaMins)

        binding.tvSpeed.text = currentSpeed.toString()
        binding.tvEtaDriver.text = arrivalTimeClock
        binding.tvDistDriver.text = BusEtaCalculator.formatDistance(distanceToNextKm)

        // AI ETA prediction calculation (Peak-hour & distance heuristic AI engine)
        val aiEtaMinutes = EtaPredictor.predict(distanceToNextKm)
        binding.tvAiPrediction.text = BusEtaCalculator.formatEtaBadge(aiEtaMinutes)

        // Top-right Live status on bottom card
        binding.tvBottomLiveRoute.text = "● LIVE · ${assignedRouteName.uppercase()}"
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
        val routeColor = getActiveRouteColor()
        val colorDone = ColorUtils.setAlphaComponent(routeColor, 120)
        val colorInactive = Color.parseColor("#CBD5E1")

        for ((idx, abbr) in routeStops.withIndex()) {
            val isPassed = idx < nextStopIndex
            val isNext   = idx == nextStopIndex
            val isActive = isNext

            val stopLayout = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity     = Gravity.CENTER
            }

            val dotSize = if (isNext) dp(14) else dp(9)
            val dot = View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(dotSize, dotSize).also { it.gravity = Gravity.CENTER_HORIZONTAL }
                background = when {
                    isNext   -> ContextCompat.getDrawable(ctx, R.drawable.ic_map_stop_next)
                    isPassed -> createCircle(colorDone)
                    isActive -> createCircle(routeColor)
                    else     -> createCircle(colorInactive)
                }
            }

            val label = TextView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.topMargin = dp(3); it.gravity = Gravity.CENTER_HORIZONTAL }
                text      = abbr
                textSize  = if (isNext) 10f else 9f
                gravity   = Gravity.CENTER
                typeface  = if (isNext) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setTextColor(when {
                    isNext   -> routeColor
                    isPassed -> colorDone
                    else     -> colorInactive
                })
            }

            stopLayout.addView(dot)
            stopLayout.addView(label)

            val wrapper = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity     = Gravity.CENTER_VERTICAL
                setPadding(dp(3), 0, dp(3), 0)
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
                    text      = " ➔ "
                    textSize  = if (isArrowActive) 14f else 11f
                    setTextColor(when { isArrowActive -> routeColor; isArrowDone -> colorDone; else -> colorInactive })
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

        binding.btnWorking.apply {
            setBackgroundResource(if (status == "WORKING") R.drawable.bg_duty_working_active else android.R.color.transparent)
            setTextColor(if (status == "WORKING") Color.WHITE else Color.parseColor("#9CA3AF"))
            typeface = if (status == "WORKING") Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        binding.btnResting.apply {
            setBackgroundResource(if (status == "RESTING") R.drawable.bg_duty_resting_active else android.R.color.transparent)
            setTextColor(if (status == "RESTING") Color.WHITE else Color.parseColor("#9CA3AF"))
            typeface = if (status == "RESTING") Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        binding.btnOffDuty.apply {
            setBackgroundResource(if (status == "OFF_DUTY") R.drawable.bg_duty_offline_active else android.R.color.transparent)
            setTextColor(if (status == "OFF_DUTY") Color.WHITE else Color.parseColor("#9CA3AF"))
            typeface = if (status == "OFF_DUTY") Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }

        currentSpeed = if (status == "WORKING") 0 else 0
        logTerminal("STATUS >> $status")

        updateBusMarkerPosition()
        updateDriverPanel()

        if (assignedBusId.isNotEmpty()) pushToFirestore()
    }

    // ════════════════════════════════════════════════════════════════════
    // REALTIME DATABASE PUSH — High frequency live location sharing
    // ════════════════════════════════════════════════════════════════════

    private fun pushToFirestore() {
        val liveStatus = when (currentStatus) {
            "WORKING" -> "Working"; "RESTING" -> "Resting"; else -> "IDLE"
        }
        val targetBusId = assignedBusId.ifEmpty { "BUS-001" }

        val liveData = hashMapOf(
            "latitude"       to currentLat,
            "longitude"      to currentLon,
            "speed"          to currentSpeed.toDouble(),
            "status"         to liveStatus,
            "nextStop"       to nextStopAbbr,
            "distanceToNext" to distanceToNextKm,
            "driverName"     to driverName,
            "routeName"      to assignedRouteName,
            "lastUpdated"    to System.currentTimeMillis()
        )

        // Push high-frequency location data to Realtime Database (0 Firestore quota consumed)
        com.upsi.smartbus.core.data.RealtimeDbHelper.busRef(targetBusId).setValue(liveData)
            .addOnSuccessListener {
                logTerminal("RTDB SYNC OK: $targetBusId ($assignedRouteName)")
            }
            .addOnFailureListener { err ->
                val errMsg = err.localizedMessage ?: "Unknown RTDB error"
                logTerminal("ERR: $errMsg")
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
                    updateNavBanner()
                }
            }
        }
    }

    private fun updateNavBanner() {
        if (_binding == null) return

        // 1. Target stop
        val targetAbbr = nextStopAbbr.ifEmpty { routeStops.getOrNull(nextStopIndex) ?: "PT" }
        val targetStop = RouteData.getStopByAbbreviation(targetAbbr)
        val targetFullName = targetStop?.fullName ?: targetAbbr

        // 2. UNIFIED DISTANCE — Strictly synchronized with bottom panel!
        val currentDistKm = distanceToNextKm
        binding.tvNavDistance.text = BusEtaCalculator.formatDistance(currentDistKm)

        // 3. Compute relative angle and navigation maneuver
        val targetLat = targetStop?.latitude ?: currentLat
        val targetLon = targetStop?.longitude ?: currentLon

        val maneuver = NavigationMathHelper.determineManeuver(
            currentLat, currentLon, targetLat, targetLon, currentDistKm, targetFullName
        )

        val iconRes = when (maneuver.type) {
            NavigationMathHelper.ManeuverType.ARRIVED -> R.drawable.ic_nav_arrive
            NavigationMathHelper.ManeuverType.STRAIGHT -> R.drawable.ic_nav_straight
            NavigationMathHelper.ManeuverType.TURN_RIGHT -> R.drawable.ic_nav_turn_right
            NavigationMathHelper.ManeuverType.TURN_LEFT -> R.drawable.ic_nav_turn_left
            NavigationMathHelper.ManeuverType.U_TURN -> R.drawable.ic_nav_uturn
            else -> R.drawable.ic_nav_straight
        }

        binding.ivNavTurnIcon.setImageResource(iconRes)
        binding.tvNavInstruction.text = maneuver.instruction

        // Next stop info
        binding.tvNavNextStop.text = targetAbbr
        val etaMins = BusEtaCalculator.calculateStandardEtaMinutes(distanceToNextKm, currentSpeed.toDouble())
        binding.tvNavEta.text = BusEtaCalculator.formatEtaBadge(etaMins)

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
            com.upsi.smartbus.core.data.RealtimeDbHelper.busRef(assignedBusId)
                .updateChildren(mapOf<String, Any>("status" to "IDLE", "speed" to 0.0, "lastUpdated" to System.currentTimeMillis()))
        }
        _binding = null
        super.onDestroyView()
    }
}
