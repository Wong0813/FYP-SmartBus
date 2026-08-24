package com.upsi.smartbus.core.util;

/**
 * AI Prediction Engine for SmartBus (Implemented in Java).
 * Demonstrates seamless Java-Kotlin interoperability within the Android project.
 * Uses traffic heuristics and the Haversine formula to predict arrival times.
 */
public class EtaPredictor {

    private static final double BASE_SPEED_KMH = 30.0;
    private static final double PEAK_HOUR_MULTIPLIER = 0.6;
    private static final double EARTH_RADIUS_KM = 6371.0;

    /**
     * Predicts arrival time in minutes based on distance and current hour of the day.
     *
     * @param distanceKm Distance remaining in kilometers
     * @param hourOfDay Current hour in 24-hour format (0-23)
     * @return Estimated arrival time in minutes (minimum 1 minute if distance > 0)
     */
    public int predictEta(double distanceKm, int hourOfDay) {
        if (distanceKm <= 0.0) {
            return 0;
        }

        double estimatedSpeed = BASE_SPEED_KMH;

        // Apply traffic heuristic based on peak hours
        if (isPeakHour(hourOfDay)) {
            estimatedSpeed *= PEAK_HOUR_MULTIPLIER;
        }

        // Time = (Distance / Speed) * 60 minutes
        double timeMinutes = (distanceKm / estimatedSpeed) * 60.0;

        return (int) Math.ceil(timeMinutes);
    }

    /**
     * Checks whether the given hour falls into UPSI campus peak hours.
     * Peak morning: 08:00 - 10:59
     * Peak afternoon/evening: 16:00 - 18:59
     *
     * @param hour Hour of day (0-23)
     * @return true if peak hour, false otherwise
     */
    public boolean isPeakHour(int hour) {
        return (hour >= 8 && hour <= 10) || (hour >= 16 && hour <= 18);
    }

    /**
     * Haversine formula to calculate the great-circle distance between two GPS coordinates.
     *
     * @param lat1 Latitude of point 1 in degrees
     * @param lon1 Longitude of point 1 in degrees
     * @param lat2 Latitude of point 2 in degrees
     * @param lon2 Longitude of point 2 in degrees
     * @return Distance in kilometers
     */
    public double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2.0) * Math.sin(dLat / 2.0)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2.0) * Math.sin(dLon / 2.0);

        double c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));

        return EARTH_RADIUS_KM * c;
    }
}
