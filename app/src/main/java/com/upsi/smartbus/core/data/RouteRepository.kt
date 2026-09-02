package com.upsi.smartbus.core.data

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.upsi.smartbus.core.model.Route
import com.upsi.smartbus.core.model.RouteData
import com.upsi.smartbus.feature.admin.seeder.AdminSortHelper

object RouteRepository {

    @Volatile
    private var cachedRoutes: List<Route> = emptyList()

    fun getCachedRoutes(): List<Route> =
        cachedRoutes.ifEmpty { AdminSortHelper.canonicalRoutes() }

    fun getWeekdayRoutes(): List<Route> =
        sortAndCache(getCachedRoutes().filter {
            it.scheduleType.equals("WEEKDAY", ignoreCase = true)
        })

    fun getSaturdayRoutes(): List<Route> =
        sortAndCache(getCachedRoutes().filter {
            it.scheduleType.equals("SATURDAY", ignoreCase = true)
        })

    fun findRoute(nameOrShort: String): Route? =
        getCachedRoutes().find {
            it.name.equals(nameOrShort, ignoreCase = true) ||
                it.shortName.equals(nameOrShort, ignoreCase = true)
        }

    fun loadRoutes(onResult: (List<Route>) -> Unit) {
        if (cachedRoutes.isNotEmpty()) {
            onResult(cachedRoutes)
            return
        }
        FirestoreHelper.db.collection("routes").get()
            .addOnSuccessListener { snapshot ->
                val fromFirestore = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(Route::class.java)?.copy(id = doc.id)
                }
                cachedRoutes = mergeWithCanonical(fromFirestore)
                onResult(cachedRoutes)
            }
            .addOnFailureListener {
                cachedRoutes = AdminSortHelper.canonicalRoutes()
                onResult(cachedRoutes)
            }
    }

    /** Always ensure all 20 official routes exist; Firestore overrides stops/details by name */
    private fun mergeWithCanonical(firestore: List<Route>): List<Route> {
        val canonical = AdminSortHelper.canonicalRoutes()
        if (firestore.isEmpty()) return canonical

        val byName = firestore.associateBy { it.name.lowercase() }
        val merged = canonical.map { base ->
            byName[base.name.lowercase()]?.copy(
                shortName = base.shortName,
                scheduleType = base.scheduleType
            ) ?: base
        }.toMutableList()

        firestore.filter { fs ->
            merged.none { it.name.equals(fs.name, ignoreCase = true) }
        }.forEach { merged.add(it) }

        return AdminSortHelper.sortRoutes(merged)
    }

    private fun sortAndCache(list: List<Route>) = AdminSortHelper.sortRoutes(list)

    fun invalidateCache() {
        cachedRoutes = emptyList()
    }
}
