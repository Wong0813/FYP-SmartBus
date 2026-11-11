package com.upsi.smartbus.core.network

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface AiApiService {
    @POST("/api/predict_eta")
    fun predictEta(@Body request: EtaRequest): Call<EtaResponse>

    @POST("/api/chat")
    fun chat(@Body request: ChatRequest): Call<ChatResponse>

    @POST("/api/recommend_route")
    fun recommendRoute(@Body request: RouteRecommendRequest): Call<RouteRecommendResponse>

    @POST("/api/anomaly_check")
    fun checkAnomaly(@Body request: AnomalyRequest): Call<AnomalyResponse>

    @GET("/api/health")
    fun healthCheck(): Call<HealthResponse>
}
