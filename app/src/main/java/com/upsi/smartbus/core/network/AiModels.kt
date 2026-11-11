package com.upsi.smartbus.core.network

// ETA
data class EtaRequest(
    val distance_km: Double,
    val current_speed_kmh: Double = 25.0,
    val route_name: String = "",
    val next_stop: String = "",
    val delay_seconds: Double = 0.0
)

data class EtaResponse(
    val status: String,
    val distance_km: Double,
    val eta_seconds: Int,
    val eta_minutes: Double,
    val eta_label: String,
    val weather_adjustment: WeatherAdjustment?,
    val model_engine: String
)

data class WeatherAdjustment(
    val condition: String?,
    val multiplier: Double,
    val advisory: String?
)

// Chat
data class ChatRequest(
    val message: String,
    val current_stop: String = ""
)

data class ChatResponse(
    val status: String,
    val query: String,
    val intent: String,
    val confidence: Double,
    val entities: ChatEntities?,
    val response: String
)

data class ChatEntities(
    val stops: List<String>?,
    val routes: List<String>?,
    val times: List<String>?
)

// Route Recommendation
data class RouteRecommendRequest(
    val origin: String,
    val destination: String,
    val day_type: String = "weekday"
)

data class RouteRecommendResponse(
    val status: String,
    val data: RouteRecommendData?
)

data class RouteRecommendData(
    val origin: String,
    val destination: String,
    val day_type: String?,
    val headway_minutes: Int?,
    val walking_total_minutes: Int?,
    val total_options: Int,
    val solutions: List<RouteSolution>
)

data class RouteSolution(
    val type: String,
    val route: String?,
    val route_first: String?,
    val transfer_stop: String?,
    val route_second: String?,
    val stops_count: Int?,
    val total_stops: Int?,
    val leg1_stops: Int?,
    val leg2_stops: Int?,
    val ride_duration_minutes: Int?,
    val wait_time_minutes: Int?,
    val est_duration_minutes: Int,
    val walking_time_minutes: Int?,
    val headway_minutes: Int?,
    val advice: String?,
    val description: String
)

// Anomaly
data class AnomalyRequest(
    val speed_kmh: Double,
    val off_route_meters: Double = 0.0,
    val halt_seconds: Double = 0.0,
    val time_since_update_s: Double = 2.0,
    val status: String = "WORKING"
)

data class AnomalyResponse(
    val status: String,
    val result: AnomalyResult?
)

data class AnomalyResult(
    val is_anomaly: Boolean,
    val severity: String,
    val alerts: List<String>?
)

// Health
data class HealthResponse(
    val status: String,
    val service: String,
    val version: String
)
