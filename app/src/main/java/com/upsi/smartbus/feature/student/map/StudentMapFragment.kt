package com.upsi.smartbus.feature.student.map

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
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
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.ListenerRegistration
import com.upsi.smartbus.R
import com.upsi.smartbus.core.data.FirestoreHelper
import com.upsi.smartbus.core.data.RouteRepository
import com.upsi.smartbus.core.model.Bus
import com.upsi.smartbus.core.model.Route
import com.upsi.smartbus.core.model.RouteData
import com.upsi.smartbus.core.util.BusEtaCalculator
import com.upsi.smartbus.core.util.BusSpeedCalculator
import com.upsi.smartbus.core.util.EtaPredictor
import com.upsi.smartbus.core.util.RouteRoadFetcher
import com.upsi.smartbus.core.util.RouteSegmentHelper
import com.upsi.smartbus.databinding.FragmentStudentMapBinding
import com.upsi.smartbus.feature.student.StudentActivity
import com.upsi.smartbus.feature.student.buses.BusDetailActivity
import java.util.Calendar

class StudentMapFragment : Fragment(), OnMapReadyCallback {

    companion object {
        private const val ARG_TARGET_ROUTE = "arg_target_route"
        fun newInstance(targetRouteName: String? = null): StudentMapFragment {
            return StudentMapFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_TARGET_ROUTE, targetRouteName)
                }
            }
        }
    }

    private var _binding: FragmentStudentMapBinding? = null
    private val binding get() = _binding!!

    private val db by lazy { FirestoreHelper.db }
    private val etaPredictor = EtaPredictor()

    private var googleMap: GoogleMap? = null
    private var availableBuses: List<Bus> = emptyList()

    // Mode: null means "All Buses" overview, non-null means focused on specific bus/route
    private var selectedBus: Bus? = null

    // Generation token to strictly reject stale async route callbacks across bus switches
    private var currentRenderToken: Long = 0L

    // Map controllers
    private val busTracker = StudentBusTracker()
    private val mapRenderer = StudentMapRenderer()

    // Firestore listeners
    private var allBusesListener: ListenerRegistration? = null

    // High-tech glowing route color palette matching bus marker colors
    private val TECH_ROUTE_COLORS = listOf(
        Color.parseColor("#00E5FF"), // 0: Cyber Cyan
        Color.parseColor("#00E676"), // 1: Neon Green
        Color.parseColor("#FF9100"), // 2: Amber Blaze
        Color.parseColor("#E040FB"), // 3: Neon Magenta
        Color.parseColor("#2979FF"), // 4: Electric Blue
        Color.parseColor("#FFD600"), // 5: Cyber Gold
        Color.parseColor("#FF5252")  // 6: Laser Coral
    )

    private val COLOR_RESTING      = Color.parseColor("#FF9800") // Amber
    private val COLOR_OFFLINE      = Color.parseColor("#78909C") // Grey Blue
    private val upsiCenter          = LatLng(3.7220, 101.5230)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStudentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        RouteRepository.loadRoutes { }
        setupClickListeners()
        updateBottomPanel()

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
            moveCamera(CameraUpdateFactory.newLatLngZoom(upsiCenter, 15.5f))
            setPadding(0, 0, 0, 300)

            setOnMarkerClickListener { marker ->
                val busId = busTracker.findBusIdByMarker(marker)
                if (busId != null) {
                    val bus = availableBuses.find { it.id == busId }
                    if (bus != null) {
                        selectBus(bus)
                    }
                }
                false
            }
        }
        listenToAllBuses()
    }

    // ══════════════════════════════════════════════════════════════════════════
    // REALTIME DATABASE LIVE BUS TRACKING
    // ══════════════════════════════════════════════════════════════════════════

    private var rtdbListener: com.google.firebase.database.ValueEventListener? = null
    private val staticBusesMap = mutableMapOf<String, Bus>()

    private fun listenToAllBuses() {
        // ✅ Use cache first — avoid Firestore reads every time the fragment opens
        if (com.upsi.smartbus.core.data.AppCache.hasStaticBuses()) {
            staticBusesMap.clear()
            com.upsi.smartbus.core.data.AppCache.getStaticBusesList().forEach { bus ->
                staticBusesMap[bus.id] = bus
            }
            bindRealtimeDatabaseListener()
            return
        }
        // Cache miss — read from Firestore once, then populate cache
        db.collection("buses").get().addOnSuccessListener { snapshot ->
            if (_binding == null) return@addOnSuccessListener
            staticBusesMap.clear()
            val busList = mutableListOf<Bus>()
            for (doc in snapshot.documents) {
                val bus = doc.toObject(Bus::class.java) ?: continue
                val b = bus.copy(id = doc.id)
                staticBusesMap[doc.id] = b
                busList.add(b)
            }
            com.upsi.smartbus.core.data.AppCache.putStaticBuses(busList)
            bindRealtimeDatabaseListener()
        }.addOnFailureListener {
            bindRealtimeDatabaseListener()
        }
    }

    private fun bindRealtimeDatabaseListener() {
        rtdbListener?.let { com.upsi.smartbus.core.data.RealtimeDbHelper.liveBuses.removeEventListener(it) }
        rtdbListener = object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                if (_binding == null || googleMap == null) return

                val fetched = mutableListOf<Bus>()
                for (child in snapshot.children) {
                    val busId = child.key ?: continue
                    val lat = child.child("latitude").getValue(Double::class.java) ?: 0.0
                    val lng = child.child("longitude").getValue(Double::class.java) ?: 0.0
                    val speed = child.child("speed").getValue(Double::class.java) ?: 0.0
                    val status = child.child("status").getValue(String::class.java) ?: "IDLE"
                    val nextStop = child.child("nextStop").getValue(String::class.java) ?: ""
                    val dist = child.child("distanceToNext").getValue(Double::class.java) ?: 0.0
                    val routeName = child.child("routeName").getValue(String::class.java) ?: ""
                    val lastUpdated = child.child("lastUpdated").getValue(Long::class.java) ?: System.currentTimeMillis()

                    val staticBus = staticBusesMap[busId]
                    val finalRouteName = routeName.ifEmpty { staticBus?.routeName.orEmpty() }
                    val finalRouteStops = staticBus?.routeStops ?: (com.upsi.smartbus.core.data.RouteRepository.findRoute(finalRouteName)?.stops ?: emptyList())

                    val isStale = (System.currentTimeMillis() - lastUpdated) > (5 * 60 * 1000L)
                    val effectiveStatus = if (isStale) "IDLE" else status
                    val effectiveSpeed = if (isStale) 0.0 else speed

                    val mergedBus = Bus(
                        id = busId,
                        name = staticBus?.name?.ifEmpty { finalRouteName } ?: finalRouteName,
                        routeName = finalRouteName,
                        plateNumber = staticBus?.plateNumber ?: busId,
                        licensePlate = staticBus?.licensePlate ?: staticBus?.plateNumber ?: busId,
                        routeStops = finalRouteStops,
                        startStop = finalRouteStops.firstOrNull().orEmpty(),
                        location = com.google.firebase.firestore.GeoPoint(lat, lng),
                        speed = effectiveSpeed,
                        nextStop = if (isStale) "" else nextStop,
                        distanceToNext = if (isStale) 0.0 else dist,
                        status = effectiveStatus,
                        photoUrl = staticBus?.photoUrl.orEmpty(),
                        lastUpdated = lastUpdated
                    )
                    fetched.add(mergedBus)
                }

                if (fetched.isEmpty()) {
                    fetched.addAll(staticBusesMap.values)
                }

                availableBuses = fetched

                updateAllBusMarkers()

                val targetRoute = arguments?.getString(ARG_TARGET_ROUTE)
                if (!targetRoute.isNullOrEmpty() && selectedBus == null) {
                    val targetBus = fetched.find { it.routeName.equals(targetRoute, true) }
                    if (targetBus != null) {
                        selectBus(targetBus)
                    }
                }

                val currentFocused = selectedBus
                if (currentFocused != null) {
                    val updated = fetched.find { it.id == currentFocused.id || it.routeName.equals(currentFocused.routeName, true) }
                    if (updated != null) {
                        selectedBus = updated
                    }
                    renderSingleRouteOnMap(selectedBus ?: currentFocused)
                } else {
                    renderAllActiveBusSegments()
                }

                updateBottomPanel()
            }

            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        }
        com.upsi.smartbus.core.data.RealtimeDbHelper.liveBuses.addValueEventListener(rtdbListener!!)
    }

    private fun updateAllBusMarkers() {
        val map = googleMap ?: return
        val ctx = context ?: return
        busTracker.updateAllMarkers(
            map = map,
            ctx = ctx,
            availableBuses = availableBuses,
            selectedBusId = selectedBus?.id,
            techRouteColors = TECH_ROUTE_COLORS,
            colorResting = COLOR_RESTING,
            colorOffline = COLOR_OFFLINE
        )
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SELECTION (ALL BUSES vs SINGLE ROUTE)
    // ══════════════════════════════════════════════════════════════════════════

    private fun selectAllBuses() {
        currentRenderToken++
        selectedBus = null
        clearAllPolylinesAndStops()
        updateAllBusMarkers()
        renderAllActiveBusSegments()
        updateBottomPanel()
        googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(upsiCenter, 15f), 600, null)
    }

    private fun selectBus(bus: Bus) {
        currentRenderToken++
        selectedBus = bus
        clearAllPolylinesAndStops()
        updateAllBusMarkers()
        renderSingleRouteOnMap(bus)
        updateBottomPanel()

        val lat = bus.location.latitude
        val lng = bus.location.longitude
        if (lat != 0.0 && lng != 0.0) {
            googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), 16.5f), 600, null)
            busTracker.getMarker(bus.id)?.showInfoWindow()
        }
    }

    private fun clearAllPolylinesAndStops() {
        mapRenderer.clearAll()
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ONLY RENDER ACTIVE SEGMENTS (Current Location ➔ Next Stop) IN MATCHING COLOR
    // ══════════════════════════════════════════════════════════════════════════

    private fun renderAllActiveBusSegments() {
        val map = googleMap ?: return
        val ctx = context ?: return
        mapRenderer.renderAllActiveBusSegments(
            map = map,
            context = ctx,
            availableBuses = availableBuses,
            techRouteColors = TECH_ROUTE_COLORS,
            token = currentRenderToken
        ) { respToken -> _binding != null && respToken == currentRenderToken }
    }

    private fun renderSingleRouteOnMap(bus: Bus) {
        val map = googleMap ?: return
        val ctx = context ?: return
        mapRenderer.renderSingleRouteOnMap(
            map = map,
            context = ctx,
            bus = bus,
            color = TECH_ROUTE_COLORS[0],
            token = currentRenderToken
        ) { respToken -> _binding != null && respToken == currentRenderToken && selectedBus?.id == bus.id }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // BOTTOM PANEL
    // ══════════════════════════════════════════════════════════════════════════

    private fun updateBottomPanel() {
        if (_binding == null) return
        val bus = selectedBus

        val activeCount = availableBuses.count {
            it.status.equals("working", true) || it.status.equals("resting", true)
        }

        if (bus == null) {
            binding.tvSelectedBus.text = "All Buses ($activeCount Active)"
            binding.busSelectorDot.setBackgroundResource(if (activeCount > 0) R.drawable.dot_green else R.drawable.dot_gray)

            // Hide bottom panel completely in All Buses overview
            binding.cardBottomPanel.visibility = View.GONE
            googleMap?.setPadding(0, 0, 0, 0)
        } else {
            // Show bottom card for the selected bus
            binding.cardBottomPanel.visibility = View.VISIBLE
            val bottomPadding = (200 * resources.displayMetrics.density).toInt()
            googleMap?.setPadding(0, 0, 0, bottomPadding)

            val isResting = bus.status.equals("resting", true)
            val isWorking = bus.status.equals("working", true)
            val speedEtaMins = BusEtaCalculator.calculateStandardEtaMinutes(bus.distanceToNext, if (isWorking) bus.speed else 0.0)
            val aiEtaMins    = EtaPredictor.predict(bus.distanceToNext)
            val plate        = bus.plateNumber.ifEmpty { bus.licensePlate.ifEmpty { "--" } }
            val stdArrivalClock = BusEtaCalculator.calculateArrivalClock(speedEtaMins)

            val dotRes = when {
                isResting -> R.drawable.dot_orange
                isWorking -> R.drawable.dot_green
                else -> R.drawable.dot_gray
            }
            binding.panelStatusDot.setBackgroundResource(dotRes)
            binding.busSelectorDot.setBackgroundResource(dotRes)

            binding.panelStatusPill.setBackgroundResource(
                when {
                    isResting -> R.drawable.bg_status_resting
                    isWorking -> R.drawable.bg_status_moving
                    else -> R.drawable.bg_status_offline
                }
            )
            binding.tvPanelStatus.text = when {
                isResting -> "RESTING"
                isWorking -> "LIVE"
                else -> "OFFLINE"
            }
            binding.tvPanelStatus.setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    when {
                        isResting -> R.color.status_resting
                        isWorking -> R.color.status_moving
                        else -> R.color.status_offline
                    }
                )
            )

            val routeObj = com.upsi.smartbus.core.data.RouteRepository.findRoute(bus.routeName) ?: com.upsi.smartbus.core.data.RouteRepository.findRoute(bus.name)
            val desc = routeObj?.shortName ?: ""
            binding.tvPanelRouteName.text = bus.routeName.ifEmpty { bus.name }
            binding.tvPanelDriverInfo.text = "${bus.driverName.ifEmpty { "UPSI Driver" }} • ${bus.id} ($plate)"

            binding.tvSelectedBus.text = if (desc.isNotEmpty()) {
                "${bus.routeName.ifEmpty { bus.name }} — $desc"
            } else {
                "${bus.routeName.ifEmpty { bus.name }} ($plate)"
            }

            if (isResting) {
                binding.tvPanelNextStop.text = "RESTING"
                binding.tvPanelDistance.text = "0 m"
                binding.tvPanelArriveClock.text = "--:--"
                binding.tvPanelAiEta.text = "Resting"
            } else {
                binding.tvPanelNextStop.text = bus.nextStop.ifEmpty { "--" }
                val stdEtaBadge = BusEtaCalculator.formatEtaBadge(speedEtaMins)
                binding.tvPanelArriveClock.text = "$stdEtaBadge ($stdArrivalClock)"
                // Immediate local heuristic display (zero latency fallback)
                binding.tvPanelAiEta.text = BusEtaCalculator.formatEtaBadge(aiEtaMins)
                // Asynchronous remote XGBoost ML model prediction
                queryRemoteAiEta(bus)
            }

            buildStopTimeline(bus)
        }
    }

    private fun queryRemoteAiEta(bus: Bus) {
        if (!bus.status.equals("working", true) || bus.distanceToNext <= 0.0) return
        val request = com.upsi.smartbus.core.network.EtaRequest(
            distance_km = bus.distanceToNext,
            current_speed_kmh = if (bus.speed > 0) bus.speed else 25.0,
            route_name = bus.routeName,
            next_stop = bus.nextStop
        )
        com.upsi.smartbus.core.network.AiApiClient.getService().predictEta(request)
            .enqueue(object : retrofit2.Callback<com.upsi.smartbus.core.network.EtaResponse> {
                override fun onResponse(
                    call: retrofit2.Call<com.upsi.smartbus.core.network.EtaResponse>,
                    response: retrofit2.Response<com.upsi.smartbus.core.network.EtaResponse>
                ) {
                    if (_binding == null || selectedBus?.id != bus.id) return
                    val body = response.body() ?: return
                    if (response.isSuccessful && body.status == "success") {
                        binding.tvPanelAiEta.text = body.eta_label
                        val weather = body.weather_adjustment
                        if (weather != null && weather.multiplier > 1.05 && !weather.advisory.isNullOrEmpty()) {
                            binding.llWeatherAdvisory.visibility = View.VISIBLE
                            binding.tvWeatherAdvisoryText.text = weather.advisory
                        }
                    }
                }

                override fun onFailure(call: retrofit2.Call<com.upsi.smartbus.core.network.EtaResponse>, t: Throwable) {
                    // Graceful offline degradation: local heuristic already rendered
                }
            })
    }

    private fun buildStopTimeline(bus: Bus) {
        val container = binding.llPanelStopsTimeline
        container.removeAllViews()

        val stops = if (bus.routeStops.isNotEmpty()) bus.routeStops
                    else com.upsi.smartbus.core.data.RouteRepository.findRoute(bus.routeName)?.stops
                    ?: listOf("KAB", "PT", "SS", "KA", "DKP", "KAB")

        val nextIdx = stops.indexOf(bus.nextStop).let { if (it == -1) 1 else it }
        val curIdx  = (nextIdx - 1).coerceAtLeast(0)

        val ctx = requireContext()
        val dp  = { n: Int -> TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, n.toFloat(), resources.displayMetrics).toInt() }

        for ((idx, abbr) in stops.withIndex()) {
            val isPassed  = idx < nextIdx
            val isNext    = abbr == bus.nextStop
            val isActive  = idx == nextIdx - 1 || isNext

            val stopLayout = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity     = Gravity.CENTER
            }

            val dot = View(ctx).apply {
                val size = if (isNext || (isPassed.not() && isActive)) dp(12) else dp(8)
                layoutParams = LinearLayout.LayoutParams(size, size).also { it.gravity = Gravity.CENTER_HORIZONTAL }
                background = when {
                    isNext   -> ContextCompat.getDrawable(ctx, R.drawable.ic_map_stop_next)
                    isPassed -> ContextCompat.getDrawable(ctx, R.drawable.ic_map_stop_default)
                    isActive -> ContextCompat.getDrawable(ctx, R.drawable.ic_map_stop_next)
                    else     -> ContextCompat.getDrawable(ctx, R.drawable.ic_map_stop_default)
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
                    isNext   -> TECH_ROUTE_COLORS[0]
                    isActive -> TECH_ROUTE_COLORS[0]
                    else     -> ContextCompat.getColor(ctx, R.color.text_secondary)
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

            if (idx < stops.size - 1) {
                val isArrowActive = (idx == curIdx)

                val arrow = TextView(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, dp(20)
                    ).also { it.gravity = Gravity.CENTER_VERTICAL }
                    text      = " ➜ "
                    textSize  = if (isArrowActive) 16f else 12f
                    setTextColor(if (isArrowActive) TECH_ROUTE_COLORS[0] else ContextCompat.getColor(ctx, R.color.text_hint))
                    typeface = if (isArrowActive) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                }
                container.addView(arrow)
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnMapDrawer.setOnClickListener {
            (activity as? StudentActivity)?.openDrawer()
        }
        binding.cardBottomPanel.setOnClickListener {
            val b = selectedBus
            if (b != null) {
                startActivity(Intent(requireContext(), BusDetailActivity::class.java).apply {
                    putExtra("bus_id", b.id)
                    putExtra("bus_name", b.name)
                })
            } else {
                showBusPickerDialog()
            }
        }
        binding.busSelector.setOnClickListener { showBusPickerDialog() }
        binding.btnFabRouteFinder.setOnClickListener {
            val dialog = RouteFinderDialog.newInstance()
            dialog.onRouteSelectedListener = { routeName ->
                val targetBus = availableBuses.find { it.routeName.equals(routeName, true) }
                if (targetBus != null) {
                    selectBus(targetBus)
                }
            }
            dialog.show(childFragmentManager, "RouteFinderDialog")
        }
        binding.btnFabAiChat.setOnClickListener {
            com.upsi.smartbus.feature.student.chat.AiChatBottomSheet.newInstance()
                .show(childFragmentManager, "AiChatBottomSheet")
        }
        binding.btnCloseWeather.setOnClickListener {
            binding.llWeatherAdvisory.visibility = View.GONE
        }
    }

    private fun showBusPickerDialog() {
        val routes = RouteRepository.getCachedRoutes()
        BusSelectorDialog.show(
            context = requireContext(),
            routes = routes,
            activeBuses = availableBuses,
            selectedRouteName = selectedBus?.routeName ?: selectedBus?.name,
            onAllBusesSelected = {
                selectAllBuses()
            },
            onRouteSelected = { selectedRoute: Route, liveBus: Bus? ->
                if (liveBus != null) {
                    selectBus(liveBus)
                } else {
                    val firstStopAbbr = selectedRoute.stops.firstOrNull() ?: ""
                    val firstStop = RouteData.getStopByAbbreviation(firstStopAbbr)
                    val loc = if (firstStop != null) GeoPoint(firstStop.latitude, firstStop.longitude) else GeoPoint(3.6845, 101.5234)

                    val fallbackBus = Bus(
                        id = "BUS-${selectedRoute.id}",
                        name = selectedRoute.name,
                        routeName = selectedRoute.name,
                        routeStops = selectedRoute.stops,
                        startStop = firstStopAbbr,
                        location = loc,
                        nextStop = selectedRoute.stops.getOrNull(1) ?: firstStopAbbr,
                        status = "Offline"
                    )
                    selectBus(fallbackBus)
                }
            }
        )
    }

    override fun onDestroyView() {
        rtdbListener?.let { com.upsi.smartbus.core.data.RealtimeDbHelper.liveBuses.removeEventListener(it) }
        allBusesListener?.remove()
        _binding = null
        super.onDestroyView()
    }
}
