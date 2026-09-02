package com.upsi.smartbus.core.data

import com.upsi.smartbus.core.model.Bus
import com.upsi.smartbus.core.model.UserProfile

/**
 * In-memory cache for Firestore static data.
 * Prevents re-fetching every time a Fragment is re-created.
 * - users: TTL 5 minutes (may be edited by admin)
 * - buses (static metadata): no TTL — only invalidated on manual Sync
 */
object AppCache {

    private const val TTL_MS = 5 * 60 * 1000L // 5 minutes

    // ── Users ──────────────────────────────────────────────────────
    private var usersData: List<UserProfile> = emptyList()
    private var usersTimestamp = 0L

    fun getCachedUsers(): List<UserProfile>? =
        if (usersData.isNotEmpty() && System.currentTimeMillis() - usersTimestamp < TTL_MS)
            usersData else null

    fun putUsers(users: List<UserProfile>) {
        usersData = users
        usersTimestamp = System.currentTimeMillis()
    }

    fun invalidateUsers() {
        usersData = emptyList()
        usersTimestamp = 0L
    }

    // ── Buses (fleet metadata / static) ────────────────────────────
    // No TTL — static data only changes when admin explicitly syncs fleet.
    // Keyed by busId for fast lookup.
    private var staticBusesMap: Map<String, Bus> = emptyMap()
    private var staticBusesLoaded = false

    /** Returns true if we have a cached copy ready to use */
    fun hasStaticBuses(): Boolean = staticBusesLoaded && staticBusesMap.isNotEmpty()

    /** Returns the full list of cached static buses */
    fun getStaticBusesList(): List<Bus> = staticBusesMap.values.toList()

    /** Returns a single bus by id, or null if not cached */
    fun getStaticBusById(id: String): Bus? = staticBusesMap[id]

    /** Store the list from Firestore — indexed by id */
    fun putStaticBuses(buses: List<Bus>) {
        staticBusesMap = buses.associateBy { it.id }
        staticBusesLoaded = true
    }

    fun invalidateStaticBuses() {
        staticBusesMap = emptyMap()
        staticBusesLoaded = false
    }

    // ── Legacy buses cache (with TTL) kept for AdminFleetFragment ──
    private var busesData: List<Bus> = emptyList()
    private var busesTimestamp = 0L

    fun getCachedBuses(): List<Bus>? =
        if (busesData.isNotEmpty() && System.currentTimeMillis() - busesTimestamp < TTL_MS)
            busesData else null

    fun putBuses(buses: List<Bus>) {
        busesData = buses
        busesTimestamp = System.currentTimeMillis()
        // Also update the static map
        putStaticBuses(buses)
    }

    fun invalidateBuses() {
        busesData = emptyList()
        busesTimestamp = 0L
        invalidateStaticBuses()
    }

    // ── Clear all ──────────────────────────────────────────────────
    fun clearAll() {
        invalidateUsers()
        invalidateBuses()
        RouteRepository.invalidateCache()
    }
}
