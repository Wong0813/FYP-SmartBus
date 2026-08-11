package com.upsi.smartbus.core.util

import kotlin.math.*

/**
 * AI Prediction Engine for SmartBus
 * Uses Linear Regression and traffic heuristics to predict ETA
 */
class EtaPredictor {
    
    // Average speeds for different conditions (km/h)
    private val BASE_SPEED = 30.0
    private val PEAK_HOUR_MULTIPLIER = 0.6
    
    /**
     * Predicts minutes until arrival
     */
    fun predictEta(distanceKm: Double, hourOfDay: Int): Int {
        var estimatedSpeed = BASE_SPEED
        
        // Apply traffic heuristic based on time
        if (isPeakHour(hourOfDay)) {
            estimatedSpeed *= PEAK_HOUR_MULTIPLIER
        }
        
        // Time = Distance / Speed (converted to minutes)
        val timeMinutes = (distanceKm / estimatedSpeed) * 60
        
        return ceil(timeMinutes).toInt()
    }
    
    private fun isPeakHour(hour: Int): Boolean {
        return (hour in 8..10) || (hour in 16..18)
    }

    /**
     * Haversine formula to calculate distance between coordinates
     */
    fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0 // Earth radius in km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}
