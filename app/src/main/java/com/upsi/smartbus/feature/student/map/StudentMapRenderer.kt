package com.upsi.smartbus.feature.student.map

import android.content.Context
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.*
import com.upsi.smartbus.core.model.Bus
import com.upsi.smartbus.core.model.RouteData
import com.upsi.smartbus.core.util.MapMarkerHelper
import com.upsi.smartbus.core.util.RouteSegmentHelper

/**
 * Student Map Road Polylines & Next Stop Dot Renderer.
 * Ensures instant cached road display and token-protected dynamic routing.
 */
class StudentMapRenderer {

    private val activePolylines = mutableMapOf<String, Polyline>()
    private val stopMarkers = mutableMapOf<String, Marker>()

    fun clearAll() {
        activePolylines.values.forEach { it.remove() }
        activePolylines.clear()
        stopMarkers.values.forEach { it.remove() }
        stopMarkers.clear()
    }

    fun renderAllActiveBusSegments(
        map: GoogleMap,
        context: Context,
        availableBuses: List<Bus>,
        techRouteColors: List<Int>,
        token: Long,
        isTokenValid: (Long) -> Boolean
    ) {
        val activeWorkingBuses = availableBuses.filter {
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
            val color = techRouteColors[index % techRouteColors.size]
            renderBusActiveSegment(map, context, bus, color, token, isTokenValid)
        }
    }

    fun renderSingleRouteOnMap(
        map: GoogleMap,
        context: Context,
        bus: Bus,
        color: Int,
        token: Long,
        isTokenValid: (Long) -> Boolean
    ) {
        val toRemovePoly = activePolylines.keys.filter { it != bus.id }
        toRemovePoly.forEach { id -> activePolylines.remove(id)?.remove() }

        val toRemoveStop = stopMarkers.keys.filter { it != bus.id }
        toRemoveStop.forEach { id -> stopMarkers.remove(id)?.remove() }

        if (bus.status.equals("resting", true)) {
            activePolylines.remove(bus.id)?.remove()
            stopMarkers.remove(bus.id)?.remove()
            return
        }

        renderBusActiveSegment(map, context, bus, color, token, isTokenValid)
    }

    private fun renderBusActiveSegment(
        map: GoogleMap,
        context: Context,
        bus: Bus,
        color: Int,
        token: Long,
        isTokenValid: (Long) -> Boolean
    ) {
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
                val newPoly = map.addPolyline(
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

        // 1. Check if cached road points exist for instant display
        val cachedRoad = RouteSegmentHelper.getCachedRoad(busPos, nextStopAbbr)
        if (cachedRoad != null && cachedRoad.size >= 2) {
            applyRoadPoints(cachedRoad)
        }

        // 2. Query true street road engine with token protection
        RouteSegmentHelper.dynamicallyRouteBusToNextStop(
            busId = bus.id,
            busPos = busPos,
            nextStopAbbr = nextStopAbbr,
            requestToken = token,
            onRoadPointsReady = { respToken, roadPts ->
                if (!isTokenValid(respToken)) return@dynamicallyRouteBusToNextStop
                applyRoadPoints(roadPts)
            }
        )

        // 3. Update target next stop dot
        val existingStop = stopMarkers[bus.id]
        if (existingStop != null) {
            existingStop.position = nextStopPos
            existingStop.title = "${nextStop.abbreviation} — ${nextStop.fullName}"
            existingStop.setIcon(MapMarkerHelper.createNextStopMarker(context, color))
        } else {
            val stopMarker = map.addMarker(
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
