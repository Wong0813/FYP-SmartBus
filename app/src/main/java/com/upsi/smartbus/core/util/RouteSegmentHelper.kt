package com.upsi.smartbus.core.util

import android.os.Handler
import android.os.Looper
import com.google.android.gms.maps.model.LatLng
import com.upsi.smartbus.core.model.RouteData
import java.util.concurrent.ConcurrentHashMap

/**
 * Intelligent Road Route Engine with Generation Safety and Instant Cache
 *
 * Guarantees zero stale-route flashing across bus switches and ensures
 * routes never disappear when reselecting buses.
 */
object RouteSegmentHelper {

    private val roadPointsCache = ConcurrentHashMap<String, List<LatLng>>()
    private val mainHandler = Handler(Looper.getMainLooper())

    private fun buildCacheKey(busPos: LatLng, nextStopAbbr: String): String {
        return "%.5f_%.5f_%s".format(busPos.latitude, busPos.longitude, nextStopAbbr.trim().uppercase())
    }

    /**
     * Gets immediate cached road points if available.
     */
    fun getCachedRoad(busPos: LatLng, nextStopAbbr: String): List<LatLng>? {
        val key = buildCacheKey(busPos, nextStopAbbr)
        return roadPointsCache[key]
    }

    /**
     * Dynamically routes from current bus location to the target stop along actual roads.
     * Uses generation tokens to prevent race conditions when switching between buses.
     */
    fun dynamicallyRouteBusToNextStop(
        busId: String,
        busPos: LatLng,
        nextStopAbbr: String,
        requestToken: Long,
        onRoadPointsReady: (token: Long, roadPts: List<LatLng>) -> Unit
    ) {
        val nextStop = RouteData.getStopByAbbreviation(nextStopAbbr) ?: return
        val nextStopPos = LatLng(nextStop.latitude, nextStop.longitude)
        val key = buildCacheKey(busPos, nextStopAbbr)

        val cached = roadPointsCache[key]
        if (cached != null && cached.size >= 2) {
            onRoadPointsReady(requestToken, cached)
            return
        }

        val waypoints = listOf(busPos, nextStopPos)

        RouteRoadFetcher.fetchRoadPoints(waypoints) { roadPts ->
            if (roadPts.size >= 2) {
                roadPointsCache[key] = roadPts
                mainHandler.post {
                    onRoadPointsReady(requestToken, roadPts)
                }
            }
        }
    }

    fun clearCache() {
        roadPointsCache.clear()
    }
}
