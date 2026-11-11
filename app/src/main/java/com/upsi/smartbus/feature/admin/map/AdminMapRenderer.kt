package com.upsi.smartbus.feature.admin.map

import android.content.Context
import android.graphics.Color
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.gms.maps.model.RoundCap
import com.upsi.smartbus.core.model.Bus
import com.upsi.smartbus.core.model.RouteData
import com.upsi.smartbus.core.util.MapMarkerHelper
import com.upsi.smartbus.core.util.RouteSegmentHelper

class AdminMapRenderer(
    private val context: Context,
    private val isFragmentAttached: () -> Boolean,
    private val getCurrentToken: () -> Long,
    private val getSelectedBus: () -> Bus?
) {
    val activePolylines = mutableMapOf<String, Polyline>()
    val stopMarkers = mutableMapOf<String, Marker>()

    val TECH_ROUTE_COLORS = listOf(
        Color.parseColor("#00E5FF"), // 0: Cyber Cyan
        Color.parseColor("#00E676"), // 1: Neon Green
        Color.parseColor("#FF9100"), // 2: Amber Blaze
        Color.parseColor("#E040FB"), // 3: Neon Magenta
        Color.parseColor("#2979FF"), // 4: Electric Blue
        Color.parseColor("#FFD600"), // 5: Cyber Gold
        Color.parseColor("#FF5252")  // 6: Laser Coral
    )

    fun clearAllPolylinesAndStops() {
        activePolylines.forEach { it.value.remove() }
        activePolylines.clear()
        stopMarkers.forEach { it.value.remove() }
        stopMarkers.clear()
    }

    fun renderAllActiveBusSegments(gMap: GoogleMap?, busList: List<Bus>) {
        val map = gMap ?: return
        val activeWorkingBuses = busList.filter {
            it.status.equals("working", true) &&
            (it.location.latitude != 0.0 || it.location.longitude != 0.0)
        }

        val activeIds = activeWorkingBuses.map { it.id }.toSet()

        val polylinesToRemove = activePolylines.keys.filter { it !in activeIds }
        polylinesToRemove.forEach { id -> activePolylines.remove(id)?.remove() }

        val stopsToRemove = stopMarkers.keys.filter { it !in activeIds }
        stopsToRemove.forEach { id -> stopMarkers.remove(id)?.remove() }

        activeWorkingBuses.forEachIndexed { index, bus ->
            val color = TECH_ROUTE_COLORS[index % TECH_ROUTE_COLORS.size]
            renderBusActiveSegment(map, bus, color, getCurrentToken())
        }
    }

    fun renderSingleRouteOnMap(gMap: GoogleMap?, bus: Bus) {
        val map = gMap ?: return

        val toRemovePoly = activePolylines.keys.filter { it != bus.id }
        toRemovePoly.forEach { id -> activePolylines.remove(id)?.remove() }

        val toRemoveStop = stopMarkers.keys.filter { it != bus.id }
        toRemoveStop.forEach { id -> stopMarkers.remove(id)?.remove() }

        if (bus.status.equals("resting", true)) {
            activePolylines.remove(bus.id)?.remove()
            stopMarkers.remove(bus.id)?.remove()
            return
        }

        val color = TECH_ROUTE_COLORS[0]
        renderBusActiveSegment(map, bus, color, getCurrentToken())
    }

    fun renderBusActiveSegment(gMap: GoogleMap, bus: Bus, color: Int, token: Long) {
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

        val cachedRoad = RouteSegmentHelper.getCachedRoad(busPos, nextStopAbbr)
        if (cachedRoad != null && cachedRoad.size >= 2) {
            applyRoadPoints(cachedRoad)
        }

        RouteSegmentHelper.dynamicallyRouteBusToNextStop(
            busId = bus.id,
            busPos = busPos,
            nextStopAbbr = nextStopAbbr,
            requestToken = token,
            onRoadPointsReady = { respToken, roadPts ->
                if (!isFragmentAttached() || respToken != getCurrentToken()) return@dynamicallyRouteBusToNextStop
                val focused = getSelectedBus()
                if (focused != null && focused.id != bus.id) return@dynamicallyRouteBusToNextStop

                applyRoadPoints(roadPts)
            }
        )

        val existingStop = stopMarkers[bus.id]
        if (existingStop != null) {
            existingStop.position = nextStopPos
            existingStop.title = "${nextStop.abbreviation} — ${nextStop.fullName}"
            existingStop.setIcon(MapMarkerHelper.createNextStopMarker(context, color))
        } else {
            val stopMarker = gMap.addMarker(
                MarkerOptions()
                    .position(nextStopPos)
                    .title("${nextStop.abbreviation} — ${nextStop.fullName}")
                    .snippet("🎯 Next Destination Stop")
                    .icon(MapMarkerHelper.createNextStopMarker(context, color))
                    .anchor(0.5f, 0.5f)
                    .zIndex(6f)
            )
            if (stopMarker != null) stopMarkers[bus.id] = stopMarker
        }
    }
}
