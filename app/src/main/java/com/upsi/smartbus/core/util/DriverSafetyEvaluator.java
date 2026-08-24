package com.upsi.smartbus.core.util;

/**
 * Driver Safety and Speed Compliance Rule Engine (Implemented in Java).
 * Analyzes telemetry data against campus speed limits and evaluates driving risk levels.
 */
public class DriverSafetyEvaluator {

    public static final double DEFAULT_CAMPUS_SPEED_LIMIT = 40.0; // km/h
    public static final double RESIDENTIAL_ZONE_LIMIT = 25.0;     // km/h
    public static final double SEVERE_SPEEDING_THRESHOLD = 55.0;   // km/h

    public enum SafetyLevel {
        NORMAL,
        SPEEDING_WARNING,
        SEVERE_SPEEDING,
        STOPPED
    }

    /**
     * Evaluates the current speed compliance.
     *
     * @param currentSpeedKmh Current speed in km/h
     * @param speedLimitKmh Speed limit for the route/zone
     * @return SafetyLevel enum indicating risk level
     */
    public static SafetyLevel evaluateSpeed(double currentSpeedKmh, double speedLimitKmh) {
        if (currentSpeedKmh <= 0.5) {
            return SafetyLevel.STOPPED;
        }
        if (currentSpeedKmh > SEVERE_SPEEDING_THRESHOLD) {
            return SafetyLevel.SEVERE_SPEEDING;
        }
        if (currentSpeedKmh > speedLimitKmh) {
            return SafetyLevel.SPEEDING_WARNING;
        }
        return SafetyLevel.NORMAL;
    }

    /**
     * Calculates an overall driving safety score (0 - 100).
     *
     * @param totalDistanceKm Total distance driven in kilometers
     * @param warningCount Number of mild speeding violations
     * @param severeCount Number of severe speeding violations
     * @return Score from 0 to 100
     */
    public static int calculateSafetyScore(double totalDistanceKm, int warningCount, int severeCount) {
        int baseScore = 100;
        int penalty = (warningCount * 3) + (severeCount * 10);
        int finalScore = baseScore - penalty;
        return Math.max(0, Math.min(100, finalScore));
    }

    /**
     * Formats a speed alert badge text.
     */
    public static String getSpeedAlertLabel(double speed, double limit) {
        if (speed > limit) {
            int over = (int) Math.round(speed - limit);
            return "⚠️ +" + over + " km/h OVER";
        }
        return "NORMAL";
    }
}
