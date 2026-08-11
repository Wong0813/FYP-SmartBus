package com.upsi.smartbus.feature.student.map

import android.app.AlertDialog
import android.content.Intent
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
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.ListenerRegistration
import com.upsi.smartbus.R
import com.upsi.smartbus.core.data.FirestoreHelper
import com.upsi.smartbus.core.data.RouteRepository
import com.upsi.smartbus.core.model.Bus
import com.upsi.smartbus.core.model.Route
import com.upsi.smartbus.core.model.RouteData
import com.upsi.smartbus.core.util.EtaPredictor
import com.upsi.smartbus.core.util.RouteRoadFetcher
import com.upsi.smartbus.databinding.FragmentStudentMapBinding
import com.upsi.smartbus.feature.student.buses.BusDetailActivity
import java.util.Calendar

class StudentMapFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentStudentMapBinding? = null
    private val binding get() = _binding!!

    private val db by lazy { FirestoreHelper.db }
    private val etaPredictor = EtaPredictor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var googleMap: GoogleMap? = null
    private var availableBuses: List<Bus> = emptyList()
    private var selectedBus: Bus = defaultBus()

    // Map overlays
    private var busMarker: Marker? = null
    private val stopMarkers = mutableListOf<Marker>()
    // Multiple segment polylines: index i = segment from stop[i] to stop[i+1]
    private val segmentPolylines = mutableListOf<Polyline>()
    // Road geometry cache per segment key
    private val segmentRoadCache = mutableMapOf<String, List<LatLng>>()

    // Firestore listeners
    private var selectedBusListener: ListenerRegistration? = null
    private var allBusesListener: ListenerRegistration? = null

    // ── Google-Maps-style blue for active/nav, grey for inactive ──
    private val COLOR_ACTIVE   = Color.parseColor("#1A73E8")   // Google Blue
    private val COLOR_ACTIVE_A = Color.argb(255, 26, 115, 232) // full opacity
    private val COLOR_INACTIVE = Color.parseColor("#BDBDBD")    // medium grey
    private val COLOR_DONE     = Color.parseColor("#90CAF9")    // lighter blue (already passed)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStudentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        RouteRepository.loadRoutes { }
        populateBottomPanel(selectedBus)
        setupClickListeners()
        
        var mapFragment = childFragmentManager.findFragmentById(R.id.mapView) as? SupportMapFragment
        if (mapFragment == null) {
            mapFragment = SupportMapFragment.newInstance()
            childFragmentManager.beginTransaction()
                .replace(R.id.mapView, mapFragment)
                .commitAllowingStateLoss()
        }
        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        map.apply {
            uiSettings.isZoomControlsEnabled = false
            uiSettings.isMapToolbarEnabled = false
            uiSettings.isCompassEnabled = true
            uiSettings.isTiltGesturesEnabled = false
            val upsi = LatLng(3.7220, 101.5230)
            moveCamera(CameraUpdateFactory.newLatLngZoom(upsi, 16.0f))
            setPadding(0, 0, 0, 300)  // bottom padding for card
        }
        listenToAllBuses()
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MAP: RENDER ALL SEGMENTS WITH PROPER COLORS
    // Each segment i→i+1 is drawn separately so we can color each independently:
    //   • Already passed segments → lighter blue (done)
    //   • Current active segment (bus is on this) → bold Google-blue
    //   • Future segments → dashed grey
    // ══════════════════════════════════════════════════════════════════════════

    private fun renderRouteOnMap(bus: Bus) {
        val map = googleMap ?: return

        // Clear old overlays
        segmentPolylines.forEach { it.remove() }
        segmentPolylines.clear()
        stopMarkers.forEach { it.remove() }
        stopMarkers.clear()

        val stops = if (bus.routeStops.isNotEmpty()) bus.routeStops
                    else listOf("KAB", "PT", "SS", "KA", "DKP", "KAB")

        val nextIdx = stops.indexOf(bus.nextStop).let { if (it == -1) 1 else it }
        val currentSegmentIndex = (nextIdx - 1).coerceAtLeast(0)

        // Draw each segment i → i+1
        for (i in 0 until stops.size - 1) {
            val fromAbbr = stops[i]
            val toAbbr   = stops[i + 1]
            val fromStop = RouteData.getStopByAbbreviation(fromAbbr) ?: continue
            val toStop   = RouteData.getStopByAbbreviation(toAbbr) ?: continue

            val fromPt = LatLng(fromStop.latitude, fromStop.longitude)
            val toPt   = LatLng(toStop.latitude,   toStop.longitude)

            val segKey = "${fromAbbr}_${toAbbr}"

            val isActive = (i == currentSegmentIndex)
            val isDone   = (i < currentSegmentIndex)

            val cached = segmentRoadCache[segKey]
            if (cached != null && cached.isNotEmpty()) {
                val poly = drawSegmentReturn(map, cached, isActive, isDone)
                segmentPolylines.add(poly)
            } else {
                val fallback = listOf(fromPt, toPt)
                val poly = drawSegmentReturn(map, fallback, isActive, isDone)
                segmentPolylines.add(poly)

                RouteRoadFetcher.fetchRoadPoints(fallback) { roadPts ->
                    mainHandler.post {
                        if (_binding == null || selectedBus.id != bus.id) return@post
                        if (roadPts.size > 2) {
                            segmentRoadCache[segKey] = roadPts
                            // Re-render route to cleanly swap polyline
                            renderRouteOnMap(selectedBus)
                        }
                    }
                }
            }
        }

        // Draw stop markers
        for ((idx, abbr) in stops.withIndex()) {
            val stop = RouteData.getStopByAbbreviation(abbr) ?: continue
            val pos  = LatLng(stop.latitude, stop.longitude)

            val isNextStop  = (abbr == bus.nextStop)
            val isStartStop = (idx == 0)
            val isPassed    = (idx < nextIdx)

            val iconRes = when {
                isNextStop  -> R.drawable.ic_map_stop_next
                isStartStop -> R.drawable.ic_map_stop_start
                isPassed    -> R.drawable.ic_map_stop_default
                else        -> R.drawable.ic_map_stop_default
            }

            val marker = map.addMarker(
                MarkerOptions()
                    .position(pos)
                    .title("${stop.abbreviation} — ${stop.fullName}")
                    .snippet(when {
                        isNextStop  -> "🎯 Next Stop"
                        isStartStop -> "🏁 Start"
                        isPassed    -> "✅ Passed"
                        else        -> "Bus Stop ${idx + 1}/${stops.size}"
                    })
                    .icon(vectorToBitmap(iconRes))
                    .anchor(0.5f, 0.5f)
                    .zIndex(if (isNextStop) 4f else 2f)
            )
            if (marker != null) stopMarkers.add(marker)
        }
    }

    private fun drawSegmentReturn(map: GoogleMap, pts: List<LatLng>, isActive: Boolean, isDone: Boolean): Polyline {
        return when {
            isActive -> map.addPolyline(
                PolylineOptions()
                    .addAll(pts)
                    .color(COLOR_ACTIVE)
                    .width(18f)
                    .geodesic(true)
                    .jointType(JointType.ROUND)
                    .startCap(RoundCap())
                    .endCap(RoundCap())
                    .zIndex(5f)
            )
            isDone -> map.addPolyline(
                PolylineOptions()
                    .addAll(pts)
                    .color(COLOR_DONE)
                    .width(10f)
                    .geodesic(true)
                    .jointType(JointType.ROUND)
                    .startCap(RoundCap())
                    .endCap(RoundCap())
                    .zIndex(3f)
            )
            else -> map.addPolyline(      // future
                PolylineOptions()
                    .addAll(pts)
                    .color(COLOR_INACTIVE)
                    .width(10f)
                    .geodesic(true)
                    .pattern(listOf(Dash(25f), Gap(12f)))
                    .jointType(JointType.ROUND)
                    .startCap(RoundCap())
                    .endCap(RoundCap())
                    .zIndex(2f)
            )
        }
    }

    private fun updateBusMarker(bus: Bus) {
        val map = googleMap ?: return
        val busPos = if (bus.location.latitude != 0.0)
            LatLng(bus.location.latitude, bus.location.longitude)
        else LatLng(3.722061, 101.516966)

        if (busMarker == null) {
            busMarker = map.addMarker(
                MarkerOptions()
                    .position(busPos)
                    .icon(vectorToBitmap(R.drawable.ic_map_bus_marker))
                    .anchor(0.5f, 0.5f)
                    .zIndex(10f)
            )
        } else {
            busMarker?.position = busPos
        }
        val plate = bus.plateNumber.ifEmpty { bus.licensePlate.ifEmpty { "--" } }
        busMarker?.title = "${bus.routeName.ifEmpty { bus.name }} [$plate]"
        busMarker?.snippet = "📍 Next: ${bus.nextStop}  |  🚀 ${bus.speed.toInt()} km/h  |  🕒 ~${estimateEtaMins(bus)} min"

        map.animateCamera(CameraUpdateFactory.newLatLng(busPos), 800, null)
    }

    private fun estimateEtaMins(bus: Bus): Int {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return etaPredictor.predictEta(bus.distanceToNext, h)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // BOTTOM PANEL: Full info + stop timeline
    // ══════════════════════════════════════════════════════════════════════════

    private fun populateBottomPanel(bus: Bus) {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val speedEtaMins = if (bus.speed > 0) ((bus.distanceToNext / bus.speed) * 60).toInt().coerceAtLeast(1) else 1
        val aiEtaMins    = etaPredictor.predictEta(bus.distanceToNext, h)
        val plate        = bus.plateNumber.ifEmpty { bus.licensePlate.ifEmpty { "WAA 1234 A" } }

        // Compute clock times for arrival
        val clockFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        val nowMs = System.currentTimeMillis()
        val stdArrivalClock = clockFormat.format(java.util.Date(nowMs + speedEtaMins * 60 * 1000L))
        val aiArrivalClock  = clockFormat.format(java.util.Date(nowMs + aiEtaMins * 60 * 1000L))

        // Status dot
        val dotRes = when (bus.status) {
            "Working" -> R.drawable.dot_green
            "Resting" -> R.drawable.dot_orange
            else      -> R.drawable.dot_gray
        }
        binding.statusDotLarge.setBackgroundResource(dotRes)
        binding.busSelectorDot.setBackgroundResource(dotRes)

        // Route name + plate
        binding.tvRouteName.text   = bus.routeName.ifEmpty { bus.name }
        binding.tvPlateBadge.text  = plate
        
        val routeObj = RouteData.getAllRoutes().find { it.name == bus.routeName || it.name == bus.name }
        val desc = routeObj?.shortName ?: ""
        binding.tvSelectedBus.text = if (desc.isNotEmpty()) {
            "${bus.routeName.ifEmpty { bus.name }} — $desc"
        } else {
            "${bus.routeName.ifEmpty { bus.name }} ($plate)"
        }

        // Metrics: Next Stop, Distance, Arrival Clock, AI ETA
        val nextStop = RouteData.getStopByAbbreviation(bus.nextStop)
        binding.tvNextStop.text     = bus.nextStop.ifEmpty { "--" }
        binding.tvNextStopFull.text = nextStop?.fullName ?: ""
        
        // Display distance (e.g. 1.2 km or 800 m)
        val distKm = bus.distanceToNext
        binding.tvDistance.text = if (distKm < 1.0 && distKm > 0) {
            "${(distKm * 1000).toInt()} m"
        } else {
            "%.1f km".format(distKm)
        }

        // Display clock time (e.g. 14:28 (in 3m))
        binding.tvArrivalClock.text = stdArrivalClock
        binding.tvEta.text          = "($speedEtaMins m)"
        binding.tvAiEta.text        = aiArrivalClock

        // Stop timeline
        buildStopTimeline(bus)
    }

    private fun buildStopTimeline(bus: Bus) {
        val container = binding.llStopTimeline
        container.removeAllViews()

        val stops   = if (bus.routeStops.isNotEmpty()) bus.routeStops
                      else listOf("KAB", "PT", "SS", "KA", "DKP", "KAB")
        val nextIdx = stops.indexOf(bus.nextStop).let { if (it == -1) 1 else it }
        val curIdx  = (nextIdx - 1).coerceAtLeast(0) // segment currently active

        val ctx = requireContext()
        val dp  = { n: Int -> TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, n.toFloat(), resources.displayMetrics).toInt() }

        for ((idx, abbr) in stops.withIndex()) {
            val isPassed  = idx < nextIdx
            val isNext    = abbr == bus.nextStop
            val isActive  = idx == nextIdx - 1 || isNext  // current or upcoming stop in active seg

            // ── Stop dot + label ──
            val stopLayout = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity     = Gravity.CENTER
            }

            val dot = View(ctx).apply {
                val size = if (isNext || isPassed.not() && isActive) dp(12) else dp(8)
                layoutParams = LinearLayout.LayoutParams(size, size).also { it.gravity = Gravity.CENTER_HORIZONTAL }
                background = when {
                    isNext   -> ContextCompat.getDrawable(ctx, R.drawable.ic_map_stop_next)
                    isPassed -> createCircle(ctx, COLOR_DONE)
                    isActive -> createCircle(ctx, COLOR_ACTIVE)
                    else     -> createCircle(ctx, COLOR_INACTIVE)
                }
            }

            val label = TextView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.topMargin = dp(3); it.gravity = Gravity.CENTER_HORIZONTAL }
                text      = abbr
                textSize  = 9f
                gravity   = Gravity.CENTER
                typeface  = if (isNext || isActive) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setTextColor(when {
                    isNext   -> COLOR_ACTIVE
                    isPassed -> COLOR_DONE
                    isActive -> COLOR_ACTIVE
                    else     -> COLOR_INACTIVE
                })
            }

            stopLayout.addView(dot)
            stopLayout.addView(label)

            // Wrap with horizontal padding
            val wrapper = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity     = Gravity.CENTER_VERTICAL
                setPadding(dp(2), 0, dp(2), 0)
            }
            wrapper.addView(stopLayout)
            container.addView(wrapper)

            // ── Arrow connector between stops (not after last) ──
            if (idx < stops.size - 1) {
                val isArrowActive = (idx == curIdx)     // between bus prev and next
                val isArrowDone   = (idx < curIdx)

                val arrow = TextView(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, dp(20)
                    ).also { it.gravity = Gravity.CENTER_VERTICAL }
                    text      = " ➜ "
                    textSize  = if (isArrowActive) 16f else 12f
                    setTextColor(when {
                        isArrowActive -> COLOR_ACTIVE
                        isArrowDone   -> COLOR_DONE
                        else          -> COLOR_INACTIVE
                    })
                    typeface = if (isArrowActive) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                }
                container.addView(arrow)
            }
        }
    }

    /** Creates a simple circle drawable programmatically */
    @Suppress("UNUSED_PARAMETER")
    private fun createCircle(ctx: android.content.Context, color: Int): android.graphics.drawable.ShapeDrawable {
        return android.graphics.drawable.ShapeDrawable(android.graphics.drawable.shapes.OvalShape()).apply {
            paint.color = color
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // FIRESTORE
    // ══════════════════════════════════════════════════════════════════════════

    private fun listenToAllBuses() {
        // Automatically start listening to BUS-001 document directly
        selectBus(defaultBus())

        allBusesListener = db.collection("buses")
            .addSnapshotListener { snap, err ->
                if (_binding == null) return@addSnapshotListener
                if (err != null) {
                    android.util.Log.e("StudentMap", "Error listening to buses collection", err)
                    return@addSnapshotListener
                }
                if (snap != null && !snap.isEmpty) {
                    val fetched = snap.documents.mapNotNull { runCatching { it.toObject(Bus::class.java) }.getOrNull() }
                    if (fetched.isNotEmpty()) {
                        availableBuses = fetched
                        val currentTargetId = selectedBus.id
                        val updatedTarget = fetched.find { it.id == currentTargetId }
                        if (updatedTarget != null) {
                            selectedBus = updatedTarget
                            refreshMap(updatedTarget)
                        }
                    }
                }
            }
    }

    private fun selectBus(bus: Bus) {
        selectedBus = bus
        refreshMap(bus)

        selectedBusListener?.remove()
        selectedBusListener = null

        // Only listen to Firestore if this bus has a real document
        if (availableBuses.any { it.id == bus.id }) {
            selectedBusListener = db.collection("buses").document(bus.id)
                .addSnapshotListener { doc, err ->
                    if (_binding == null) return@addSnapshotListener
                    if (err != null) {
                        android.util.Log.e("StudentMap", "Error listening to single bus: ${bus.id}", err)
                        return@addSnapshotListener
                    }
                    if (doc != null && doc.exists()) {
                        runCatching { doc.toObject(Bus::class.java) }.getOrNull()?.let { updated ->
                            selectedBus = updated
                            refreshMap(updated)
                        }
                    }
                }
        }
    }

    private fun refreshMap(bus: Bus) {
        populateBottomPanel(bus)
        renderRouteOnMap(bus)
        updateBusMarker(bus)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // CLICK / PICKER
    // ══════════════════════════════════════════════════════════════════════════

    private fun setupClickListeners() {
        binding.etaCard.setOnClickListener {
            startActivity(Intent(requireContext(), BusDetailActivity::class.java).apply {
                putExtra("bus_id", selectedBus.id)
                putExtra("bus_name", selectedBus.name)
            })
        }
        binding.busSelector.setOnClickListener { showBusPickerDialog() }
    }

    private fun showBusPickerDialog() {
        val routes = RouteRepository.getCachedRoutes()
        val items = mutableListOf<Any>()
        items.add("Weekday Routes")
        items.addAll(routes.filter { it.scheduleType.equals("WEEKDAY", ignoreCase = true) })
        items.add("Saturday Routes")
        items.addAll(routes.filter { it.scheduleType.equals("SATURDAY", ignoreCase = true) })

        val labels = items.map {
            if (it is String) {
                "=== $it ==="
            } else {
                val route = it as Route
                "${route.name} — ${route.shortName}"
            }
        }.toTypedArray()

        AlertDialog.Builder(requireContext())
            .setTitle("🚌  Select Laluan / Bus")
            .setItems(labels) { _, which ->
                val selected = items[which]
                if (selected is String) return@setItems // Ignore header clicks
                
                val selectedRoute = selected as Route
                val liveBus = availableBuses.find { it.routeName == selectedRoute.name }
                
                if (liveBus != null) {
                    selectBus(liveBus)
                } else {
                    val firstStopAbbr = selectedRoute.stops.firstOrNull() ?: ""
                    val firstStop = RouteData.getStopByAbbreviation(firstStopAbbr)
                    val loc = if (firstStop != null) GeoPoint(firstStop.latitude, firstStop.longitude) else GeoPoint(3.6845, 101.5234)
                    
                    val dummyBus = Bus(
                        id = "LOCAL-${selectedRoute.id}",
                        name = selectedRoute.name,
                        routeName = selectedRoute.name,
                        routeStops = selectedRoute.stops,
                        startStop = firstStopAbbr,
                        location = loc,
                        nextStop = selectedRoute.stops.getOrNull(1) ?: firstStopAbbr,
                        status = "Offline"
                    )
                    selectBus(dummyBus)
                }
            }
            .show()
    }

    // ══════════════════════════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════════════════════════

    private fun vectorToBitmap(res: Int): BitmapDescriptor {
        val d = ContextCompat.getDrawable(requireContext(), res)
            ?: return BitmapDescriptorFactory.defaultMarker()
        val width = d.intrinsicWidth.coerceAtLeast(1)
        val height = d.intrinsicHeight.coerceAtLeast(1)
        val bm = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bm)
        d.setBounds(0, 0, cv.width, cv.height)
        d.draw(cv)
        return BitmapDescriptorFactory.fromBitmap(bm)
    }

    override fun onDestroyView() {
        selectedBusListener?.remove()
        allBusesListener?.remove()
        _binding = null
        super.onDestroyView()
    }

    private fun defaultBus() = Bus(
        id = "BUS-001", name = "Laluan 1",
        plateNumber = "WAA 1234 A", licensePlate = "WAA 1234 A",
        routeName = "Laluan 1",
        routeStops = listOf("KAB", "PT", "SS", "KA", "DKP", "KAB"),
        startStop = "KAB",
        location = GeoPoint(3.722061, 101.516966),
        speed = 0.0, nextStop = "PT",
        distanceToNext = 1.2, passengerCount = 0, capacity = 40,
        status = "Working", lastUpdated = System.currentTimeMillis()
    )
}
