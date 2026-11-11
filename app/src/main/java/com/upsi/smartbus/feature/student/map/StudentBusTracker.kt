package com.upsi.smartbus.feature.student.map

import android.animation.ValueAnimator
import android.content.Context
import android.view.animation.AccelerateDecelerateInterpolator
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.upsi.smartbus.core.model.Bus
import com.upsi.smartbus.core.util.MapMarkerHelper
import kotlin.math.abs

/**
 * Student Map Real-time Bus Marker & Animation Controller.
 * Manages vehicle marker creation, smooth position interpolation, and recycling.
 */
class StudentBusTracker {

    private val busMarkers = mutableMapOf<String, Marker>()

    fun getMarker(busId: String): Marker? = busMarkers[busId]

    fun findBusIdByMarker(marker: Marker): String? {
        return busMarkers.entries.find { it.value == marker }?.key
    }

    fun updateAllMarkers(
        map: GoogleMap,
        ctx: Context,
        availableBuses: List<Bus>,
        selectedBusId: String?,
        techRouteColors: List<Int>,
        colorResting: Int,
        colorOffline: Int
    ) {
        val seenIds = mutableSetOf<String>()

        val activeList = availableBuses.filter {
            (it.status.equals("working", true) || it.status.equals("resting", true)) &&
                    (it.location.latitude != 0.0 || it.location.longitude != 0.0)
        }

        for (bus in availableBuses) {
            val lat = bus.location.latitude
            val lng = bus.location.longitude
            if (lat == 0.0 && lng == 0.0) continue

            seenIds.add(bus.id)
            val pos = LatLng(lat, lng)
            val isSelected = selectedBusId == bus.id

            // Determine matching color for bus
            val busColor = when {
                bus.status.equals("resting", true) -> colorResting
                bus.status.equals("working", true) -> {
                    val activeIndex = activeList.indexOfFirst { it.id == bus.id }
                    if (activeIndex != -1) techRouteColors[activeIndex % techRouteColors.size]
                    else techRouteColors[0]
                }
                else -> colorOffline
            }

            val markerResult = MapMarkerHelper.createBusMarkerWithInfoCard(
                ctx = ctx,
                bus = bus,
                color = busColor,
                isSelected = isSelected
            )
            val plate = bus.plateNumber.ifEmpty { bus.licensePlate.ifEmpty { "--" } }
            val title = "${bus.routeName.ifEmpty { bus.name }} [$plate]"
            val snippet = "Status: ${bus.status.uppercase()} | Next: ${bus.nextStop.ifEmpty { "--" }}"

            val existing = busMarkers[bus.id]
            if (existing != null) {
                animateMarkerTo(existing, pos)
                existing.title = title
                existing.snippet = snippet
                existing.setIcon(markerResult.descriptor)
                existing.setAnchor(markerResult.anchorX, markerResult.anchorY)
                existing.zIndex = if (isSelected) 12f else 8f
            } else {
                val marker = map.addMarker(
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
        if (abs(startPos.latitude - targetPos.latitude) < 0.000001 &&
            abs(startPos.longitude - targetPos.longitude) < 0.000001
        ) {
            return
        }

        // Keep marker upright for clear horizontal card reading
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

    fun clearAll() {
        busMarkers.values.forEach { it.remove() }
        busMarkers.clear()
    }
}
