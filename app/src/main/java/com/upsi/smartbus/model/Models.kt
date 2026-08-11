package com.upsi.smartbus.model

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
    val lastUpdated: Long = System.currentTimeMillis()
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
    val assignedRoute: String = ""
)

data class Route(
    val id: String = "",
    val name: String = "",
    val shortName: String = "",
    val stops: List<String> = emptyList(),
    val scheduleType: String = "WEEKDAY"
)

data class BusStop(
    val abbreviation: String = "",
    val fullName: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
)
