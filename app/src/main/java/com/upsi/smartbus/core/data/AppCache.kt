package com.upsi.smartbus.core.data

import com.upsi.smartbus.core.model.Bus
import com.upsi.smartbus.core.model.UserProfile

/**
 * In-memory cache for Firestore static data.
 * Prevents re-fetching every time a Fragment is re-created.
 * TTL = 5 minutes — stale data is refreshed automatically.
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

    // ── Buses ──────────────────────────────────────────────────────
    private var busesData: List<Bus> = emptyList()
    private var busesTimestamp = 0L

    fun getCachedBuses(): List<Bus>? =
        if (busesData.isNotEmpty() && System.currentTimeMillis() - busesTimestamp < TTL_MS)
            busesData else null

    fun putBuses(buses: List<Bus>) {
        busesData = buses
        busesTimestamp = System.currentTimeMillis()
    }

    fun invalidateBuses() {
        busesData = emptyList()
        busesTimestamp = 0L
    }

    // ── Clear all ──────────────────────────────────────────────────
    fun clearAll() {
        invalidateUsers()
        invalidateBuses()
        RouteRepository.invalidateCache()
    }
}
