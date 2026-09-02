package com.upsi.smartbus.core.model

import com.google.firebase.firestore.GeoPoint

data class Bus(
    val id: String = "",
    val name: String = "",
    val plateNumber: String = "",
    val licensePlate: String = "",
    val routeName: String = "",
    val routeStops: List<String> = emptyList(),
    val startStop: String = "",
    val location: GeoPoint = GeoPoint(0.0, 0.0),
    val speed: Double = 0.0,
    val nextStop: String = "",
    val distanceToNext: Double = 0.0,
    val passengerCount: Int = 0,
    val capacity: Int = 40,
    val status: String = "IDLE",
    val photoUrl: String = "",
    val driverName: String = "",
    val lastUpdated: Long = System.currentTimeMillis()
)

data class LiveBusLocation(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val speed: Double = 0.0,
    val status: String = "IDLE",
    val nextStop: String = "",
    val distanceToNext: Double = 0.0,
    val driverName: String = "",
    val routeName: String = "",
    val lastUpdated: Long = 0L
)

enum class UserRole {
    STUDENT, DRIVER, ADMIN
}

data class UserProfile(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val role: String = "STUDENT",
    val faculty: String = "",
    val program: String = "",
    val staffNo: String = "",
    val assignedBus: String = "",
    val assignedRoute: String = "",
    val plateNumber: String = "",
    val accountType: String = "AUTH",
    val driverNumber: Int = 0,
    val photoUrl: String = ""
)

data class Route(
    val id: String = "",
    val name: String = "",
    val shortName: String = "",
    val stops: List<String> = emptyList(),
    val scheduleType: String = "WEEKDAY",
    val routeOrder: Int = 0
)

data class BusStop(
    val abbreviation: String = "",
    val fullName: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
)
