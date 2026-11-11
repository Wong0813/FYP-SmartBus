package com.upsi.smartbus.feature.admin.map

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.fragment.app.Fragment
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.firebase.firestore.ListenerRegistration
import com.upsi.smartbus.R
import com.upsi.smartbus.core.data.FirestoreHelper
import com.upsi.smartbus.core.data.RouteRepository
import com.upsi.smartbus.core.model.Bus
import com.upsi.smartbus.core.model.Route
import com.upsi.smartbus.core.model.RouteData
import com.upsi.smartbus.core.util.BusEtaCalculator
import com.upsi.smartbus.core.util.BusSpeedCalculator
import com.upsi.smartbus.core.util.RouteRoadFetcher
import com.upsi.smartbus.core.util.RouteSegmentHelper
import com.upsi.smartbus.databinding.FragmentAdminLiveMapBinding
import com.upsi.smartbus.feature.student.map.BusSelectorDialog

class AdminLiveMapFragment : Fragment(), OnMapReadyCallback {

    companion object {
        private const val ARG_TARGET_ROUTE = "arg_target_route"
        fun newInstance(targetRouteName: String? = null): AdminLiveMapFragment {
            return AdminLiveMapFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_TARGET_ROUTE, targetRouteName)
                }
            }
        }
    }

    private var _binding: FragmentAdminLiveMapBinding? = null
    private val binding get() = _binding!!
    private val db by lazy { FirestoreHelper.db }

    private var map: GoogleMap? = null
    private val busMarkers = mutableMapOf<String, Marker>()
    private val mapRenderer by lazy {
        AdminMapRenderer(
            context = requireContext(),
            isFragmentAttached = { _binding != null },
            getCurrentToken = { currentRenderToken },
            getSelectedBus = { selectedBus }
        )
    }

    private val busList = mutableListOf<Bus>()
    private var busListener: ListenerRegistration? = null

    // Initial route target requested by Dashboard navigation
    private var initialTargetRoute: String? = null

    // Mode: null means "All Buses", non-null means focused on specific bus/route
    private var selectedBus: Bus? = null

    // Generation token to strictly reject stale async route callbacks across bus switches
    private var currentRenderToken: Long = 0L

    private val COLOR_RESTING      = Color.parseColor("#FF9800") // Amber
    private val COLOR_OFFLINE      = Color.parseColor("#78909C") // Grey Blue
    private val upsiCenter          = LatLng(3.7220, 101.5230)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminLiveMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initialTargetRoute = arguments?.getString(ARG_TARGET_ROUTE)
        RouteRepository.loadRoutes { }

        val mapFragment = childFragmentManager
            .findFragmentById(R.id.adminMapView) as SupportMapFragment
        mapFragment.getMapAsync(this)

        binding.btnMapDrawer.setOnClickListener {
            (activity as? com.upsi.smartbus.feature.admin.AdminActivity)?.openDrawer()
        }

        binding.busSelector.setOnClickListener {
            showBusPickerDialog()
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        map?.apply {
            uiSettings.isZoomControlsEnabled = true
            uiSettings.isCompassEnabled = true
            moveCamera(CameraUpdateFactory.newLatLngZoom(upsiCenter, 15f))
            setPadding(0, 0, 0, 180)

            setOnMarkerClickListener { marker ->
                val busId = busMarkers.entries.find { it.value == marker }?.key
                if (busId != null) {
                    val bus = busList.find { it.id == busId }
                    if (bus != null) {
                        selectBus(bus)
                    }
                }
                false
            }
        }
        listenToBuses()
    }

    // ══════════════════════════════════════════════════════════════════════════
    // REALTIME DATABASE LIVE BUS TRACKING
    // ══════════════════════════════════════════════════════════════════════════

    private var rtdbListener: com.google.firebase.database.ValueEventListener? = null
    private val staticBusesMap = mutableMapOf<String, Bus>()

    private fun listenToBuses() {
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
                if (_binding == null || map == null) return

                busList.clear()
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
                    busList.add(mergedBus)
                }

                // If no RTDB buses yet, populate from static map for offline visibility
                if (busList.isEmpty()) {
                    busList.addAll(staticBusesMap.values)
                }

                // Auto-focus if arriving from Dashboard route click
                val targetRoute = initialTargetRoute
                if (targetRoute != null) {
                    initialTargetRoute = null
                    val match = busList.find { it.routeName.equals(targetRoute, true) || it.name.equals(targetRoute, true) }
                    if (match != null) {
                        selectBus(match)
                    }
                    return
                }

                updateAllBusMarkers()

                val current = selectedBus
                if (current != null) {
                    val updated = busList.find { it.id == current.id || it.routeName.equals(current.routeName, true) }
                    if (updated != null) {
                        selectedBus = updated
                    }
                    mapRenderer.renderSingleRouteOnMap(map, selectedBus ?: current)
                } else {
                    mapRenderer.renderAllActiveBusSegments(map, busList)
                }

                updateStatusPanel()
            }

            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        }
        com.upsi.smartbus.core.data.RealtimeDbHelper.liveBuses.addValueEventListener(rtdbListener!!)
    }

    private fun updateAllBusMarkers() {
        val gMap = map ?: return
        val seenIds = mutableSetOf<String>()
        val ctx = context ?: return

        val activeList = busList.filter {
            (it.status.equals("working", true) || it.status.equals("resting", true)) &&
            (it.location.latitude != 0.0 || it.location.longitude != 0.0)
        }

        for (bus in busList) {
            val lat = bus.location.latitude
            val lng = bus.location.longitude
            if (lat == 0.0 && lng == 0.0) continue

            seenIds.add(bus.id)
            val pos = LatLng(lat, lng)
            val isSelected = selectedBus?.id == bus.id

            // Determine matching color for bus
            val busColor = when {
                bus.status.equals("resting", true) -> COLOR_RESTING
                bus.status.equals("working", true) -> {
                    val activeIndex = activeList.indexOfFirst { it.id == bus.id }
                    if (activeIndex != -1) mapRenderer.TECH_ROUTE_COLORS[activeIndex % mapRenderer.TECH_ROUTE_COLORS.size]
                    else mapRenderer.TECH_ROUTE_COLORS[0]
                }
                else -> COLOR_OFFLINE
            }

            val markerResult = com.upsi.smartbus.core.util.MapMarkerHelper.createBusMarkerWithInfoCard(
                ctx = ctx,
                bus = bus,
                color = busColor,
                isSelected = isSelected
            )
            val title = "${bus.routeName.ifEmpty { bus.name }} [${bus.plateNumber.ifEmpty { bus.licensePlate }}]"
            val snippet = "${bus.id} | Status: ${bus.status.uppercase()}"

            val existing = busMarkers[bus.id]
            if (existing != null) {
                animateMarkerTo(existing, pos)
                existing.title = title
                existing.snippet = snippet
                existing.setIcon(markerResult.descriptor)
                existing.setAnchor(markerResult.anchorX, markerResult.anchorY)
                existing.zIndex = if (isSelected) 12f else 8f
            } else {
                val marker = gMap.addMarker(
                    MarkerOptions()
                        .position(pos)
                        .title(title)
                        .snippet(snippet)
                        .icon(markerResult.descriptor)
                        .anchor(markerResult.anchorX, markerResult.anchorY)
                        .zIndex(if (isSelected) 12f else 8f)
                )
                if (marker != null) busMarkers[bus.id] = marker
            }
        }

        val toRemove = busMarkers.keys.filter { it !in seenIds }
        toRemove.forEach { id ->
            busMarkers.remove(id)?.remove()
        }
    }

    private fun animateMarkerTo(marker: Marker, targetPos: LatLng, duration: Long = 400L) {
        val startPos = marker.position
        if (Math.abs(startPos.latitude - targetPos.latitude) < 0.000001 &&
            Math.abs(startPos.longitude - targetPos.longitude) < 0.000001) {
            return
        }

        // Keep marker rotation upright so floating card is always horizontal
        marker.rotation = 0f

        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.duration = duration
        animator.interpolator = AccelerateDecelerateInterpolator()
        animator.addUpdateListener { animation ->
            val v = animation.animatedFraction
            val lat = v * targetPos.latitude + (1 - v) * startPos.latitude
            val lng = v * targetPos.longitude + (1 - v) * startPos.longitude
            marker.position = LatLng(lat, lng)
        }
        animator.start()
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SELECTION (ALL BUSES vs SINGLE ROUTE)
    // ══════════════════════════════════════════════════════════════════════════

    private fun selectAllBuses() {
        currentRenderToken++
        selectedBus = null
        mapRenderer.clearAllPolylinesAndStops()
        updateAllBusMarkers()
        mapRenderer.renderAllActiveBusSegments(map, busList)
        updateStatusPanel()
        map?.animateCamera(CameraUpdateFactory.newLatLngZoom(upsiCenter, 15f), 600, null)
    }

    private fun selectBus(bus: Bus) {
        currentRenderToken++
        selectedBus = bus
        mapRenderer.clearAllPolylinesAndStops()
        updateAllBusMarkers()
        mapRenderer.renderSingleRouteOnMap(map, bus)
        updateStatusPanel()

        val lat = bus.location.latitude
        val lng = bus.location.longitude
        if (lat != 0.0 && lng != 0.0) {
            map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), 16.5f), 600, null)
            busMarkers[bus.id]?.showInfoWindow()
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // STATUS PANEL & BUS PICKER
    // ══════════════════════════════════════════════════════════════════════════

    private fun updateStatusPanel() {
        if (_binding == null) return
        var active = 0; var resting = 0; var offline = 0
        busList.forEach {
            when (it.status.lowercase()) {
                "working" -> active++
                "resting" -> resting++
                else -> offline++
            }
        }

        binding.tvLiveBusCount.text = "$active Active Buses"

        val bus = selectedBus
        if (bus == null) {
            binding.tvSelectedBus.text = "All Buses"
            binding.busSelectorDot.setBackgroundResource(if (active > 0) R.drawable.dot_green else R.drawable.dot_gray)

            // Hide bottom panel completely in All Buses mode
            binding.cardBottomPanel.visibility = View.GONE
            map?.setPadding(0, 0, 0, 0)
        } else {
            // Show bottom card for the focused bus
            binding.cardBottomPanel.visibility = View.VISIBLE
            binding.llSingleBusMetrics.visibility = View.VISIBLE
            val bottomPadding = (190 * resources.displayMetrics.density).toInt()
            map?.setPadding(0, 0, 0, bottomPadding)

            val isResting = bus.status.equals("resting", true)
            val isWorking = bus.status.equals("working", true)
            val routeObj = com.upsi.smartbus.core.data.RouteRepository.findRoute(bus.routeName)
            val desc = routeObj?.shortName ?: ""
            binding.tvSelectedBus.text = if (desc.isNotEmpty()) "${bus.routeName.ifEmpty { bus.id }} — $desc" else bus.routeName.ifEmpty { bus.id }

            val dot = when {
                isResting -> R.drawable.dot_orange
                isWorking -> R.drawable.dot_green
                else -> R.drawable.dot_gray
            }
            binding.busSelectorDot.setBackgroundResource(dot)
            binding.panelStatusDot.setBackgroundResource(dot)

            binding.tvPanelRouteName.text = bus.routeName.ifEmpty { bus.name.ifEmpty { bus.id } }
            binding.tvPanelStatus.text = bus.status.uppercase()

            val plate = bus.plateNumber.ifEmpty { bus.licensePlate.ifEmpty { "--" } }
            binding.tvPanelDriverInfo.text = if (isResting) {
                "Driver resting • Bus: ${bus.id} [$plate]"
            } else if (isWorking) {
                "Live Tracking • Bus: ${bus.id} [$plate]"
            } else {
                "Offline in depot • Bus: ${bus.id} [$plate]"
            }

            // Unified next stop display: Next: SS (0.8 km)
            val dist = bus.distanceToNext
            val nextStopName = bus.nextStop.ifEmpty { "Depot" }
            binding.tvPanelNextStop.text = if (dist > 0 && isWorking) {
                "$nextStopName (${BusEtaCalculator.formatDistance(dist)})"
            } else {
                nextStopName
            }

            binding.tvPanelSpeed.text = if (isWorking) BusSpeedCalculator.formatSpeed(bus.speed) else "0 km/h"

            val etaMins = BusEtaCalculator.calculateStandardEtaMinutes(dist, if (isWorking) bus.speed else 0.0)
            binding.tvPanelEta.text = if (isWorking && dist > 0) BusEtaCalculator.formatEtaBadge(etaMins) else "—"

            // Asynchronous remote AI ETA query
            if (isWorking && dist > 0) {
                queryAdminAiEta(bus)
            }

            // Pure green stop chain highlight matching Dashboard
            val stops = bus.routeStops.ifEmpty { routeObj?.stops ?: emptyList() }
            if (stops.isNotEmpty()) {
                val currentTarget = bus.nextStop.trim()
                val targetIdx = stops.indexOfFirst { it.trim().equals(currentTarget, ignoreCase = true) }
                val html = if (targetIdx >= 0) {
                    val sb = StringBuilder()
                    stops.forEachIndexed { idx, stopName ->
                        if (idx > 0) sb.append(" <font color='#D1D5DB'>\u2192</font> ")
                        when {
                            idx == targetIdx -> sb.append("<font color='#059669'><b>$stopName</b></font>")
                            idx < targetIdx -> sb.append("<font color='#9CA3AF'>$stopName</font>")
                            else -> sb.append("<font color='#6B7280'>$stopName</font>")
                        }
                    }
                    sb.toString()
                } else {
                    stops.joinToString(" <font color='#D1D5DB'>\u2192</font> ") { "<font color='#6B7280'>$it</font>" }
                }
                binding.tvPanelStopsChain.text = androidx.core.text.HtmlCompat.fromHtml(html, androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY)
            } else {
                binding.tvPanelStopsChain.text = "Tracking live bus route"
            }
        }
    }

    private fun showBusPickerDialog() {
        val routes = RouteRepository.getCachedRoutes()
        BusSelectorDialog.show(
            context = requireContext(),
            routes = routes,
            activeBuses = busList,
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
                    val loc = if (firstStop != null) com.google.firebase.firestore.GeoPoint(firstStop.latitude, firstStop.longitude)
                              else com.google.firebase.firestore.GeoPoint(3.6845, 101.5234)

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


    private fun queryAdminAiEta(bus: Bus) {
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
                        val stdEta = BusEtaCalculator.calculateStandardEtaMinutes(bus.distanceToNext, if (bus.status.equals("working", true)) bus.speed else 0.0)
                        val stdBadge = BusEtaCalculator.formatEtaBadge(stdEta)
                        binding.tvPanelEta.text = "$stdBadge (AI: ${body.eta_label})"
                    }
                }

                override fun onFailure(call: retrofit2.Call<com.upsi.smartbus.core.network.EtaResponse>, t: Throwable) {
                    // Fallback already rendered
                }
            })
    }

    override fun onDestroyView() {
        rtdbListener?.let { com.upsi.smartbus.core.data.RealtimeDbHelper.liveBuses.removeEventListener(it) }
        busListener?.remove()
        _binding = null
        super.onDestroyView()
    }
}
