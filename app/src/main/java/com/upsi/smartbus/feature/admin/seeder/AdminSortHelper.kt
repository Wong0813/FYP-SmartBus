package com.upsi.smartbus.feature.admin.seeder

import com.upsi.smartbus.core.data.RouteRepository
import com.upsi.smartbus.core.model.Route
import com.upsi.smartbus.core.model.RouteData
import com.upsi.smartbus.core.model.UserProfile

object AdminSortHelper {

    /** Laluan 1 → 1 … Laluan 18 → 18, Shuttle KAB → 901, Shuttle KUO → 902 */
    fun routeOrderKey(route: Route): Int {
        val num = Regex("Laluan\\s+(\\d+)", RegexOption.IGNORE_CASE)
            .find(route.name)?.groupValues?.get(1)?.toIntOrNull()
        if (num != null) return num
        return when {
            route.name.contains("Shuttle Campus KAB", ignoreCase = true) ||
                route.shortName.equals("SC1", ignoreCase = true) -> 901
            route.name.contains("Shuttle Campus KUO", ignoreCase = true) ||
                route.shortName.equals("SC2", ignoreCase = true) -> 902
            else -> 999
        }
    }

    fun busOrderKey(busId: String): Int {
        val num = Regex("BUS-(\\d+)", RegexOption.IGNORE_CASE)
            .find(busId)?.groupValues?.get(1)?.toIntOrNull()
        if (num != null) return num
        val sc = Regex("BUS-SC(\\d+)", RegexOption.IGNORE_CASE)
            .find(busId)?.groupValues?.get(1)?.toIntOrNull()
        if (sc != null) return 900 + sc
        return 9999
    }

    fun driverOrderKey(user: UserProfile): Int {
        if (user.driverNumber > 0) return user.driverNumber
        val route = RouteRepository.findRoute(user.assignedRoute)
        if (route != null) return routeOrderKey(route)
        val fromStaff = Regex("DRV-(\\d+)").find(user.staffNo)?.groupValues?.get(1)?.toIntOrNull()
        if (fromStaff != null) return fromStaff
        val fromEmail = Regex("driver(\\d+)@").find(user.email)?.groupValues?.get(1)?.toIntOrNull()
        if (fromEmail != null) return fromEmail
        return 9999
    }

    fun sortRoutes(routes: List<Route>): List<Route> =
        routes.sortedWith(
            compareBy<Route>(
                { routeOrderKey(it) },
                { it.name }
            )
        )

    fun sortDrivers(users: List<UserProfile>): List<UserProfile> =
        users.sortedBy { driverOrderKey(it) }

    fun sortBusesById(ids: List<String>): List<String> = ids.sortedBy { busOrderKey(it) }

    /** Canonical 20 routes in official UPSI order (1..18: Laluan 1..18, 19..20: Shuttle) */
    fun canonicalRoutes(): List<Route> {
        val all = RouteData.weekdayRoutes + RouteData.saturdayRoutes
        return sortRoutes(all)
    }

    fun routeNumberLabel(route: Route): String {
        val key = routeOrderKey(route)
        return when {
            key in 1..99 -> "#${String.format("%02d", key)}"
            key >= 901 -> "SC${key - 900}"
            else -> route.shortName
        }
    }

    fun driverNumberLabel(number: Int): String = "Driver ${String.format("%02d", number)}"

    /** 24 official canonical users (1 Admin + 20 Drivers + 3 Demo Students) */
    fun defaultCanonicalUsers(): List<UserProfile> {
        val list = mutableListOf<UserProfile>()
        // 1. Admin
        list.add(
            UserProfile(
                uid = "admin_01",
                name = "UPSI Bus Admin",
                email = "admin@upsi.edu.my",
                role = "ADMIN",
                staffNo = "ADM-01",
                accountType = "OFFICIAL"
            )
        )
        // 2. 20 Drivers
        val entries = AccountSeeder.alignedEntries()
        entries.forEach { entry ->
            list.add(
                UserProfile(
                    uid = AccountSeeder.driverDocId(entry.number),
                    name = entry.driver.driverName,
                    email = entry.driver.email,
                    role = "DRIVER",
                    staffNo = "DRV-${String.format("%02d", entry.number)}",
                    driverNumber = entry.number,
                    assignedBus = entry.driver.busId,
                    assignedRoute = entry.route.name,
                    plateNumber = entry.driver.plateNumber,
                    accountType = "OFFICIAL"
                )
            )
        }
        // 3. 3 Demo Students
        list.add(
            UserProfile(
                uid = "student_demo_01",
                name = "Ahmad Bin Ali",
                email = "student1@upsi.edu.my",
                role = "STUDENT",
                faculty = "FCI",
                program = "Software Engineering",
                staffNo = "STU-001",
                accountType = "OFFICIAL"
            )
        )
        list.add(
            UserProfile(
                uid = "student_demo_02",
                name = "Siti Nurhaliza",
                email = "student2@upsi.edu.my",
                role = "STUDENT",
                faculty = "FPM",
                program = "Pendidikan",
                staffNo = "STU-002",
                accountType = "OFFICIAL"
            )
        )
        list.add(
            UserProfile(
                uid = "student_demo_03",
                name = "Muhammad Hafiz",
                email = "student3@upsi.edu.my",
                role = "STUDENT",
                faculty = "FSS",
                program = "Pengurusan",
                staffNo = "STU-003",
                accountType = "OFFICIAL"
            )
        )
        return list
    }

    /** 20 official canonical buses (BUS-001 .. BUS-020) */
    fun defaultCanonicalBuses(): List<com.upsi.smartbus.core.model.Bus> {
        val entries = AccountSeeder.alignedEntries()
        return entries.map { entry ->
            com.upsi.smartbus.core.model.Bus(
                id = entry.driver.busId,
                name = entry.route.name,
                licensePlate = entry.driver.plateNumber,
                plateNumber = entry.driver.plateNumber,
                routeName = entry.route.name,
                routeStops = entry.route.stops,
                startStop = entry.route.stops.firstOrNull().orEmpty(),
                nextStop = entry.route.stops.getOrElse(1) { "" },
                status = "IDLE",
                capacity = 40
            )
        }
    }
}
