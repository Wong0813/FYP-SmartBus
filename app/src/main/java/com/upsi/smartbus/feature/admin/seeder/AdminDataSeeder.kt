package com.upsi.smartbus.feature.admin.seeder

import android.content.Context
import com.upsi.smartbus.core.data.RouteRepository

object AdminDataSeeder {

    private const val PREFS = "smartbus_admin"
    private const val KEY_ROUTES_SEEDED = "routes_seeded_v9"

    fun seedIfNeeded(context: Context, onComplete: (() -> Unit)? = null) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_ROUTES_SEEDED, false)) {
            onComplete?.invoke()
            return
        }
        AccountSeeder.seedEverything(context) {
            prefs.edit().putBoolean(KEY_ROUTES_SEEDED, true).apply()
            RouteRepository.invalidateCache()
            com.upsi.smartbus.core.data.AppCache.invalidateUsers()
            com.upsi.smartbus.core.data.AppCache.invalidateBuses()
            onComplete?.invoke()
        }
    }
}
