package com.upsi.smartbus.logic

import android.util.Log
import com.google.android.gms.maps.model.LatLng
import com.upsi.smartbus.model.NavStep
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

/**
 * Robust Road Geometry & Navigation Steps Fetcher
 *
 * Uses multi-server OSRM routing with automatic failover, caching,
 * and turn-by-turn navigation instruction parsing.
 */
object RouteRoadFetcher {

    private const val TAG = "RouteRoadFetcher"
    private val roadCache = ConcurrentHashMap<String, List<LatLng>>()
    private val navCache  = ConcurrentHashMap<String, List<NavStep>>()

    private val ENDPOINTS = listOf(
        "https://router.project-osrm.org/route/v1/driving/",
        "https://routing.openstreetmap.de/routed-car/route/v1/driving/"
    )

    // ═══════════════════════════════════════════════════════════
    // ROAD GEOMETRY (polyline points)
    // ═══════════════════════════════════════════════════════════

    fun fetchRoadPoints(waypoints: List<LatLng>, callback: (List<LatLng>) -> Unit) {
        if (waypoints.size < 2) { callback(waypoints); return }

        val cacheKey = waypoints.joinToString("|") { "%.6f,%.6f".format(it.latitude, it.longitude) }
        roadCache[cacheKey]?.let { if (it.isNotEmpty()) { callback(it); return } }

        thread {
            val coordsString = waypoints.joinToString(";") { "${it.longitude},${it.latitude}" }
            var resultPoints: List<LatLng>? = null

            for (baseUrl in ENDPOINTS) {
                try {
                    val urlStr = "$baseUrl$coordsString?overview=full&geometries=geojson&steps=false"
                    val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"; connectTimeout = 6000; readTimeout = 6000
                        setRequestProperty("User-Agent", "SmartBus-UPSI-AndroidApp/1.0")
                    }
                    if (conn.responseCode == 200) {
                        val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                        val routes = json.optJSONArray("routes")
                        if (routes != null && routes.length() > 0) {
                            val coords = routes.getJSONObject(0).getJSONObject("geometry").getJSONArray("coordinates")
                            val list = mutableListOf<LatLng>()
                            for (i in 0 until coords.length()) {
                                val c = coords.getJSONArray(i)
                                list.add(LatLng(c.getDouble(1), c.getDouble(0)))
                            }
                            if (list.isNotEmpty()) { resultPoints = list; break }
                        }
                    }
                } catch (e: Exception) { Log.w(TAG, "Road fetch error: ${e.message}") }
            }
            val final_ = resultPoints ?: waypoints
            if (resultPoints != null) roadCache[cacheKey] = resultPoints
            callback(final_)
        }
    }

    // ═══════════════════════════════════════════════════════════
    // NAVIGATION STEPS (turn-by-turn instructions)
    // ═══════════════════════════════════════════════════════════

    /**
     * Fetch turn-by-turn navigation steps for a full route (all waypoints at once).
     * Returns a list of NavStep with maneuver type, direction, distance, duration, road name.
     */
    fun fetchNavSteps(waypoints: List<LatLng>, stopAbbrs: List<String> = emptyList(), callback: (List<NavStep>) -> Unit) {
        if (waypoints.size < 2) { callback(emptyList()); return }

        val cacheKey = "nav_" + waypoints.joinToString("|") { "%.6f,%.6f".format(it.latitude, it.longitude) }
        navCache[cacheKey]?.let { if (it.isNotEmpty()) { callback(it); return } }

        thread {
            val coordsString = waypoints.joinToString(";") { "${it.longitude},${it.latitude}" }
            var resultSteps: List<NavStep>? = null

            for (baseUrl in ENDPOINTS) {
                try {
                    val urlStr = "$baseUrl$coordsString?overview=full&geometries=geojson&steps=true"
                    val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"; connectTimeout = 8000; readTimeout = 8000
                        setRequestProperty("User-Agent", "SmartBus-UPSI-AndroidApp/1.0")
                    }
                    if (conn.responseCode == 200) {
                        val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                        val routes = json.optJSONArray("routes")
                        if (routes != null && routes.length() > 0) {
                            val legs = routes.getJSONObject(0).optJSONArray("legs")
                            if (legs != null) {
                                val allSteps = mutableListOf<NavStep>()
                                val numLegs = legs.length()

                                for (legIdx in 0 until numLegs) {
                                    val steps = legs.getJSONObject(legIdx).optJSONArray("steps") ?: continue
                                    val targetAbbr = if (legIdx + 1 < stopAbbrs.size) stopAbbrs[legIdx + 1] else ""
                                    val isFinalLeg = (legIdx == numLegs - 1)

                                    for (stepIdx in 0 until steps.length()) {
                                        val step = steps.getJSONObject(stepIdx)
                                        val maneuver = step.optJSONObject("maneuver") ?: continue
                                        val mType = maneuver.optString("type", "continue")

                                        // Skip intermediate leg "arrive" steps (only keep the true final destination arrive)
                                        if (mType == "arrive" && !isFinalLeg) {
                                            continue
                                        }

                                        val locArr = maneuver.optJSONArray("location")
                                        val loc = if (locArr != null && locArr.length() >= 2)
                                            LatLng(locArr.getDouble(1), locArr.getDouble(0))
                                        else continue

                                        val navStep = NavStep(
                                            type = mType,
                                            modifier = maneuver.optString("modifier", "straight"),
                                            distanceMeters = step.optDouble("distance", 0.0),
                                            durationSeconds = step.optDouble("duration", 0.0),
                                            roadName = step.optString("name", ""),
                                            location = loc,
                                            targetStopAbbr = targetAbbr
                                        )

                                        // Skip zero distance filler steps unless it's the final arrive
                                        if (navStep.distanceMeters > 0 || (navStep.type == "arrive" && isFinalLeg)) {
                                            allSteps.add(navStep)
                                        }
                                    }
                                }
                                if (allSteps.isNotEmpty()) {
                                    resultSteps = allSteps
                                    Log.d(TAG, "Fetched ${allSteps.size} nav steps from $baseUrl")
                                    break
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Nav steps fetch error from $baseUrl: ${e.message}")
                }
            }

            val final_ = resultSteps ?: emptyList()
            if (resultSteps != null) navCache[cacheKey] = resultSteps
            callback(final_)
        }
    }

    fun clearCache() {
        roadCache.clear()
        navCache.clear()
    }
}
