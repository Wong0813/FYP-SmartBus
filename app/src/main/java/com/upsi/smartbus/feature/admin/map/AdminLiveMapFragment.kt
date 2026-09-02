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
    private val activePolylines = mutableMapOf<String, Polyline>()
    private val stopMarkers = mutableMapOf<String, Marker>()

    private val busList = mutableListOf<Bus>()
    private var busListener: ListenerRegistration? = null

    // Initial route target requested by Dashboard navigation
    private var initialTargetRoute: String? = null

    // Mode: null means "All Buses", non-null means focused on specific bus/route
    private var selectedBus: Bus? = null

    // Generation token to strictly reject stale async route callbacks across bus switches
    private var currentRenderToken: Long = 0L

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

                    val mergedBus = Bus(
                        id = busId,
                        name = staticBus?.name?.ifEmpty { finalRouteName } ?: finalRouteName,
                        routeName = finalRouteName,
                        plateNumber = staticBus?.plateNumber ?: busId,
                        licensePlate = staticBus?.licensePlate ?: staticBus?.plateNumber ?: busId,
                        routeStops = finalRouteStops,
                        startStop = finalRouteStops.firstOrNull().orEmpty(),
                        location = com.google.firebase.firestore.GeoPoint(lat, lng),
                        speed = speed,
                        nextStop = nextStop,
                        distanceToNext = dist,
                        status = status,
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
                    renderSingleRouteOnMap(selectedBus ?: current)
                } else {
                    renderAllActiveBusSegments()
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
                    if (activeIndex != -1) TECH_ROUTE_COLORS[activeIndex % TECH_ROUTE_COLORS.size]
                    else TECH_ROUTE_COLORS[0]
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
        clearAllPolylinesAndStops()
        updateAllBusMarkers()
        renderAllActiveBusSegments()
        updateStatusPanel()
        map?.animateCamera(CameraUpdateFactory.newLatLngZoom(upsiCenter, 15f), 600, null)
    }

    private fun selectBus(bus: Bus) {
        currentRenderToken++
        selectedBus = bus
        clearAllPolylinesAndStops()
        updateAllBusMarkers()
        renderSingleRouteOnMap(bus)
        updateStatusPanel()

        val lat = bus.location.latitude
        val lng = bus.location.longitude
        if (lat != 0.0 && lng != 0.0) {
            map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), 16.5f), 600, null)
            busMarkers[bus.id]?.showInfoWindow()
        }
    }

    private fun clearAllPolylinesAndStops() {
        activePolylines.forEach { it.value.remove() }
        activePolylines.clear()
        stopMarkers.forEach { it.value.remove() }
        stopMarkers.clear()
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ONLY RENDER ACTIVE SEGMENTS (Current Location ➔ Next Stop) IN MATCHING COLOR
    // ══════════════════════════════════════════════════════════════════════════

    private fun renderAllActiveBusSegments() {
        val gMap = map ?: return
        val activeWorkingBuses = busList.filter {
            it.status.equals("working", true) &&
            (it.location.latitude != 0.0 || it.location.longitude != 0.0)
        }

        val activeIds = activeWorkingBuses.map { it.id }.toSet()

        // Remove polylines/stops that are no longer working
        val polylinesToRemove = activePolylines.keys.filter { it !in activeIds }
        polylinesToRemove.forEach { id -> activePolylines.remove(id)?.remove() }

        val stopsToRemove = stopMarkers.keys.filter { it !in activeIds }
        stopsToRemove.forEach { id -> stopMarkers.remove(id)?.remove() }

        activeWorkingBuses.forEachIndexed { index, bus ->
            val color = TECH_ROUTE_COLORS[index % TECH_ROUTE_COLORS.size]
            renderBusActiveSegment(gMap, bus, color, currentRenderToken)
        }
    }

    private fun renderSingleRouteOnMap(bus: Bus) {
        val gMap = map ?: return

        // In single bus mode, ensure all other polylines/stops are cleared
        val toRemovePoly = activePolylines.keys.filter { it != bus.id }
        toRemovePoly.forEach { id -> activePolylines.remove(id)?.remove() }

        val toRemoveStop = stopMarkers.keys.filter { it != bus.id }
        toRemoveStop.forEach { id -> stopMarkers.remove(id)?.remove() }

        if (bus.status.equals("resting", true)) {
            // Resting bus: show clean marker at resting position, no distracting segment
            activePolylines.remove(bus.id)?.remove()
            stopMarkers.remove(bus.id)?.remove()
            return
        }

        val color = TECH_ROUTE_COLORS[0]
        renderBusActiveSegment(gMap, bus, color, currentRenderToken)
    }

    /**
     * Smooth in-place update for the active leg: Current Bus GPS ➔ Next Target Stop
     * Uses generation tokens to prevent race conditions when switching between buses.
     */
    private fun renderBusActiveSegment(gMap: GoogleMap, bus: Bus, color: Int, token: Long) {
        val busPos = LatLng(bus.location.latitude, bus.location.longitude)
        val nextStopAbbr = bus.nextStop
        val nextStop = RouteData.getStopByAbbreviation(nextStopAbbr) ?: return
        val nextStopPos = LatLng(nextStop.latitude, nextStop.longitude)

        fun applyRoadPoints(roadPts: List<LatLng>) {
            val poly = activePolylines[bus.id]
            if (poly != null) {
                poly.color = color
                poly.points = roadPts
            } else {
                val newPoly = gMap.addPolyline(
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
                activePolylines[bus.id] = newPoly
            }
        }

        // Check if cached road points exist for instant display
        val cachedRoad = RouteSegmentHelper.getCachedRoad(busPos, nextStopAbbr)
        if (cachedRoad != null && cachedRoad.size >= 2) {
            applyRoadPoints(cachedRoad)
        }

        // Query true street road engine with token protection
        RouteSegmentHelper.dynamicallyRouteBusToNextStop(
            busId = bus.id,
            busPos = busPos,
            nextStopAbbr = nextStopAbbr,
            requestToken = token,
            onRoadPointsReady = { respToken, roadPts ->
                if (_binding == null || respToken != currentRenderToken) return@dynamicallyRouteBusToNextStop
                // Double check if in single mode that this bus is still selected
                val focused = selectedBus
                if (focused != null && focused.id != bus.id) return@dynamicallyRouteBusToNextStop

                applyRoadPoints(roadPts)
            }
        )

        // Update target next stop dot (clean 14dp circle)
        val existingStop = stopMarkers[bus.id]
        if (existingStop != null) {
            existingStop.position = nextStopPos
            existingStop.title = "${nextStop.abbreviation} — ${nextStop.fullName}"
            existingStop.setIcon(createNextStopMarker(requireContext(), color))
        } else {
            val stopMarker = gMap.addMarker(
                MarkerOptions()
                    .position(nextStopPos)
                    .title("${nextStop.abbreviation} — ${nextStop.fullName}")
                    .snippet("🎯 Next Destination Stop")
                    .icon(createNextStopMarker(requireContext(), color))
                    .anchor(0.5f, 0.5f)
                    .zIndex(6f)
            )
            if (stopMarker != null) stopMarkers[bus.id] = stopMarker
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

    /**
     * Creates a glowing cyberpunk pulse circle marker in the exact color matching the active route line.
     */
    private fun createDynamicCircleMarker(ctx: Context, color: Int, sizeDp: Int = 34): BitmapDescriptor {
        val density = ctx.resources.displayMetrics.density
        val sizePx = (sizeDp * density).toInt()
        val bm = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bm)
        val center = sizePx / 2f

        // Outer glow aura
        val auraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            alpha = 50
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center, center, center - (1 * density), auraPaint)

        // Middle ring
        val midPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            alpha = 120
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center, center, center - (4 * density), midPaint)

        // Inner solid core
        val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center, center, center - (7 * density), corePaint)

        // White border
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 2.2f * density
        }
        canvas.drawCircle(center, center, center - (7 * density), strokePaint)

        return BitmapDescriptorFactory.fromBitmap(bm)
    }

    /**
     * Creates a clean matching next stop dot
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

    override fun onDestroyView() {
        rtdbListener?.let { com.upsi.smartbus.core.data.RealtimeDbHelper.liveBuses.removeEventListener(it) }
        busListener?.remove()
        _binding = null
        super.onDestroyView()
    }
}
