package com.upsi.smartbus.core.util;

import java.util.Locale;

/**
 * High-precision Speed Calculation and Evaluation Engine (Implemented in Java).
 * Provides GPS displacement velocity calculation, speed smoothing, simulated speed profiling,
 * and campus speed limit safety evaluation.
 */
public class BusSpeedCalculator {

    public static final double CAMPUS_SPEED_LIMIT_KMH = 50.0;
    public static final double CAMPUS_CAUTION_SPEED_KMH = 40.0;
    public static final double CAMPUS_AVERAGE_SPEED_KMH = 30.0;

    /**
     * Calculates real-world ground speed in km/h between two GPS coordinates and timestamps.
     *
     * @param lat1 Starting latitude
     * @param lon1 Starting longitude
     * @param time1Ms Starting timestamp in milliseconds
     * @param lat2 Ending latitude
     * @param lon2 Ending longitude
     * @param time2Ms Ending timestamp in milliseconds
     * @return Calculated speed in km/h (0.0 to 120.0 km/h, clamped against GPS spikes)
     */
    public static double calculateGpsSpeedKmh(
            double lat1, double lon1, long time1Ms,
            double lat2, double lon2, long time2Ms
    ) {
        long deltaMs = Math.abs(time2Ms - time1Ms);
        if (deltaMs < 200) { // Below 200ms, GPS noise can cause infinite speed spikes
            return 0.0;
        }

        double distanceMeters = GeoSpatialCalculator.calculateDistanceMeters(lat1, lon1, lat2, lon2);
        if (distanceMeters < 0.5) { // Insignificant jitter
            return 0.0;
        }

        double deltaSeconds = deltaMs / 1000.0;
        double speedMps = distanceMeters / deltaSeconds;
        double speedKmh = speedMps * 3.6;

        // Clamp to realistic campus bus speed range (0 to 90 km/h)
        return Math.max(0.0, Math.min(90.0, speedKmh));
    }

    /**
     * Determines the driving speed for the simulation / D-PAD driver mode.
     *
     * @param stepSizeIndex Step size index (0: ~20m, 1: ~50m, 2: ~100m)
     * @param status Driver status ("WORKING", "RESTING", "OFF_DUTY")
     * @return Simulated speed integer in km/h
     */
    public static int determineSimulatedSpeed(int stepSizeIndex, String status) {
        if (status == null || !status.equalsIgnoreCase("WORKING")) {
            return 0;
        }

        switch (stepSizeIndex) {
            case 0:
                return 25; // 25 km/h for precision maneuvering
            case 1:
                return 38; // 38 km/h for standard cruising
            case 2:
                return 50; // 50 km/h for express / main road
            default:
                return 30;
        }
    }

    /**
     * Applies Exponential Moving Average (EMA) to smooth speed transitions.
     *
     * @param currentSmoothedSpeed Current smoothed speed
     * @param newRawSpeed Newly measured raw speed
     * @param alpha Smoothing factor between 0.0 (retain old) and 1.0 (instant change)
     * @return Smoothed speed in km/h
     */
    public static double smoothSpeed(double currentSmoothedSpeed, double newRawSpeed, double alpha) {
        double clampedAlpha = Math.max(0.05, Math.min(0.95, alpha));
        return (clampedAlpha * newRawSpeed) + ((1.0 - clampedAlpha) * currentSmoothedSpeed);
    }

    /**
     * Checks whether the bus is exceeding the campus speed limit.
     */
    public static boolean isSpeeding(double speedKmh) {
        return speedKmh > CAMPUS_SPEED_LIMIT_KMH;
    }

    /**
     * Checks whether the bus speed is in caution zone.
     */
    public static boolean isCautionSpeed(double speedKmh) {
        return speedKmh > CAMPUS_CAUTION_SPEED_KMH && speedKmh <= CAMPUS_SPEED_LIMIT_KMH;
    }

    /**
     * Formats speed value into clean display string (e.g. "32 km/h" or "0 km/h").
     */
    public static String formatSpeed(double speedKmh) {
        int rounded = (int) Math.round(Math.max(0.0, speedKmh));
        return rounded + " km/h";
    }

    /**
     * Formats speed integer value.
     */
    public static String formatSpeed(int speedKmh) {
        return Math.max(0, speedKmh) + " km/h";
    }
}
