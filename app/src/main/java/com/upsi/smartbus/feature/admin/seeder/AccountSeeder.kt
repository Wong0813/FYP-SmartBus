package com.upsi.smartbus.feature.admin.seeder

import com.google.firebase.firestore.SetOptions
import com.upsi.smartbus.core.data.FirestoreHelper
import com.upsi.smartbus.core.data.RouteRepository
import com.upsi.smartbus.core.model.DriverAccount
import com.upsi.smartbus.core.model.Route
import com.upsi.smartbus.core.model.RouteData

object AccountSeeder {

    private const val PREFS = "smartbus_admin"
    private const val KEY_ACCOUNTS_SEEDED = "accounts_seeded_v4"

    data class AlignedEntry(
        val number: Int,
        val route: Route,
        val driver: DriverAccount
    )

    fun alignedEntries(): List<AlignedEntry> {
        val routes = AdminSortHelper.canonicalRoutes()
        val driverMap = RouteData.defaultDrivers.associateBy { it.routeName }
        return routes.mapIndexed { index, route ->
            val num = index + 1
            val driver = driverMap[route.name] ?: DriverAccount(
                routeName = route.name,
                driverName = "Driver ${String.format("%02d", num)}",
                email = "driver$num@upsi.edu.my",
                busId = "BUS-${String.format("%03d", num)}",
                plateNumber = "WAA ${1000 + num} X"
            )
            AlignedEntry(num, route, driver)
        }
    }

    fun seedAccountsIfNeeded(context: android.content.Context, onComplete: (() -> Unit)? = null) {
        val prefs = context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_ACCOUNTS_SEEDED, false)) {
            onComplete?.invoke()
            return
        }
        seedEverything {
            prefs.edit().putBoolean(KEY_ACCOUNTS_SEEDED, true).apply()
            onComplete?.invoke()
        }
    }

    fun seedOfficialRoutes(onComplete: (() -> Unit)? = null) {
        val db = FirestoreHelper.db
        val routes = AdminSortHelper.canonicalRoutes()
        var done = 0
        routes.forEachIndexed { index, route ->
            val docId = routeDocId(route.name)
            db.collection("routes").document(docId).set(
                mapOf(
                    "id" to docId,
                    "name" to route.name,
                    "shortName" to route.shortName,
                    "stops" to route.stops,
                    "scheduleType" to route.scheduleType,
                    "routeOrder" to (index + 1)
                ),
                SetOptions.merge()
            ).addOnCompleteListener {
                done++
                if (done == routes.size) {
                    RouteRepository.invalidateCache()
                    onComplete?.invoke()
                }
            }
        }
    }

    fun seedOfficialDrivers(
        onProgress: ((current: Int, total: Int) -> Unit)? = null,
        onComplete: ((success: Int, failed: Int) -> Unit)? = null
    ) {
        cleanupLegacyDrivers {
            writeOfficialDrivers(onProgress, onComplete)
        }
    }

    private fun cleanupLegacyDrivers(onDone: () -> Unit) {
        FirestoreHelper.db.collection("users").get()
            .addOnSuccessListener { snap ->
                val batch = FirestoreHelper.db.batch()
                snap.documents.filter { doc ->
                    doc.id.startsWith("driver_laluan") ||
                        doc.id.startsWith("driver_shuttle") ||
                        doc.id.startsWith("driver_kitaran")
                }.forEach { batch.delete(it.reference) }
                batch.commit().addOnCompleteListener { onDone() }
            }
            .addOnFailureListener { onDone() }
    }

    private fun writeOfficialDrivers(
        onProgress: ((current: Int, total: Int) -> Unit)?,
        onComplete: ((success: Int, failed: Int) -> Unit)?
    ) {
        val db = FirestoreHelper.db
        val entries = alignedEntries()
        var success = 0
        var failed = 0
        var completed = 0

        entries.forEach { entry ->
            val docId = driverDocId(entry.number)
            val profile = mapOf(
                "uid" to docId,
                "name" to entry.driver.driverName,
                "email" to entry.driver.email,
                "role" to "DRIVER",
                "staffNo" to "DRV-${String.format("%02d", entry.number)}",
                "driverNumber" to entry.number,
                "assignedBus" to entry.driver.busId,
                "assignedRoute" to entry.route.name,
                "plateNumber" to entry.driver.plateNumber,
                "accountType" to "PORTAL",
                "faculty" to "",
                "program" to ""
            )
            db.collection("users").document(docId).set(profile, SetOptions.merge())
                .addOnSuccessListener {
                    success++; completed++
                    onProgress?.invoke(completed, entries.size)
                    if (completed == entries.size) onComplete?.invoke(success, failed)
                }
                .addOnFailureListener {
                    failed++; completed++
                    onProgress?.invoke(completed, entries.size)
                    if (completed == entries.size) onComplete?.invoke(success, failed)
                }
        }
    }

    fun seedOfficialBuses(onComplete: ((count: Int) -> Unit)? = null) {
        val db = FirestoreHelper.db
        val entries = alignedEntries()
        var done = 0
        entries.forEach { entry ->
            val busData = mapOf(
                "id" to entry.driver.busId,
                "name" to entry.route.name,
                "licensePlate" to entry.driver.plateNumber,
                "plateNumber" to entry.driver.plateNumber,
                "routeName" to entry.route.name,
                "routeStops" to entry.route.stops,
                "startStop" to entry.route.stops.firstOrNull().orEmpty(),
                "status" to "IDLE",
                "speed" to 0.0,
                "nextStop" to entry.route.stops.getOrElse(1) { "" },
                "distanceToNext" to 0.0,
                "passengerCount" to 0,
                "capacity" to 40,
                "driverName" to entry.driver.driverName,
                "driverNumber" to entry.number,
                "lastUpdated" to System.currentTimeMillis()
            )
            db.collection("buses").document(entry.driver.busId)
                .set(busData, SetOptions.merge())
                .addOnCompleteListener {
                    done++
                    if (done == entries.size) onComplete?.invoke(done)
                }
        }
    }

    fun seedDemoStudents(onComplete: ((count: Int) -> Unit)? = null) {
        val db = FirestoreHelper.db
        val students = listOf(
            mapOf("uid" to "student_demo_01", "name" to "Ahmad Bin Ali", "email" to "student1@upsi.edu.my", "role" to "STUDENT", "faculty" to "FCI", "program" to "Software Engineering", "accountType" to "PORTAL", "staffNo" to "STU-001"),
            mapOf("uid" to "student_demo_02", "name" to "Siti Nurhaliza", "email" to "student2@upsi.edu.my", "role" to "STUDENT", "faculty" to "FPM", "program" to "Pendidikan", "accountType" to "PORTAL", "staffNo" to "STU-002"),
            mapOf("uid" to "student_demo_03", "name" to "Muhammad Hafiz", "email" to "student3@upsi.edu.my", "role" to "STUDENT", "faculty" to "FSS", "program" to "Pengurusan", "accountType" to "PORTAL", "staffNo" to "STU-003")
        )
        var done = 0
        students.forEach { profile ->
            db.collection("users").document(profile["uid"] as String)
                .set(profile, SetOptions.merge())
                .addOnCompleteListener {
                    done++
                    if (done == students.size) onComplete?.invoke(done)
                }
        }
    }

    fun seedEverything(onComplete: (() -> Unit)? = null) {
        seedOfficialRoutes {
            seedOfficialDrivers { _, _ ->
                seedOfficialBuses {
                    seedDemoStudents { onComplete?.invoke() }
                }
            }
        }
    }

    fun driverDocId(number: Int): String = "driver_${String.format("%02d", number)}"

    fun routeDocId(routeName: String): String =
        "route_${routeName.replace(" ", "_").lowercase()}"
}
