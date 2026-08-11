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
                { if (it.scheduleType.equals("SATURDAY", true)) 1 else 0 },
                { routeOrderKey(it) },
                { it.name }
            )
        )

    fun sortDrivers(users: List<UserProfile>): List<UserProfile> =
        users.sortedBy { driverOrderKey(it) }

    fun sortBusesById(ids: List<String>): List<String> = ids.sortedBy { busOrderKey(it) }

    /** Canonical 20 routes in official UPSI order */
    fun canonicalRoutes(): List<Route> {
        val weekday = sortRoutes(RouteData.weekdayRoutes)
        val saturday = sortRoutes(RouteData.saturdayRoutes)
        return weekday + saturday
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
}
