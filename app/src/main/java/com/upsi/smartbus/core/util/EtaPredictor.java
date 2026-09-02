package com.upsi.smartbus.core.util;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * AI Heuristic Arrival Prediction Engine (Implemented in Java).
 * Uses multi-factor heuristics (campus geography, peak hours traffic multipliers,
 * dwell time buffering, and the Haversine formula) to generate accurate arrival predictions.
 */
public class EtaPredictor {

    private static final double BASE_SPEED_KMH = 30.0;
    private static final double PEAK_HOUR_MULTIPLIER = 0.65; // Peak slowdown to ~19.5 km/h
    private static final double OFF_PEAK_MULTIPLIER = 1.05;  // Free flow cruising ~31.5 km/h
    private static final double EARTH_RADIUS_KM = 6371.0;

    /**
     * Predicts arrival time in minutes based on distance and current hour of the day.
     *
     * @param distanceKm Distance remaining in kilometers
     * @param hourOfDay Current hour in 24-hour format (0-23)
     * @return Estimated arrival time in minutes (minimum 1 minute if distance > 0)
     */
    public int predictEta(double distanceKm, int hourOfDay) {
        return predict(distanceKm, hourOfDay);
    }

    /**
     * Static convenience method to predict arrival time in minutes using explicit hour.
     */
    public static int predict(double distanceKm, int hourOfDay) {
        if (distanceKm <= 0.03) {
            return 0; // Less than 30m is considered arrived
        }

        double estimatedSpeed = BASE_SPEED_KMH;

        // Apply traffic heuristic based on campus peak hours
        if (isPeakHour(hourOfDay)) {
            estimatedSpeed *= PEAK_HOUR_MULTIPLIER;
        } else {
            estimatedSpeed *= OFF_PEAK_MULTIPLIER;
        }

        // Time in minutes = (Distance / Speed) * 60 minutes
        double timeMinutes = (distanceKm / estimatedSpeed) * 60.0;

        // Add 30-second buffer for traffic lights and turns
        timeMinutes += 0.5;

        return (int) Math.max(1, Math.round(timeMinutes));
    }

    /**
     * Static convenience method to predict arrival time using current system hour.
     */
    public static int predict(double distanceKm) {
        int currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        return predict(distanceKm, currentHour);
    }

    /**
     * Predicts arrival clock string (e.g. "16:45") using current system time.
     */
    public static String predictArrivalClock(double distanceKm) {
        int etaMinutes = predict(distanceKm);
        long targetMs = System.currentTimeMillis() + (etaMinutes * 60L * 1000L);
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        return sdf.format(new Date(targetMs));
    }

    /**
     * Checks whether the given hour falls into UPSI campus peak hours.
     * Peak morning: 08:00 - 10:59
     * Peak afternoon/evening: 16:00 - 18:59
     *
     * @param hour Hour of day (0-23)
     * @return true if peak hour, false otherwise
     */
    public static boolean isPeakHour(int hour) {
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
        return calculateDistanceKm(lat1, lon1, lat2, lon2);
    }

    /**
     * Static Haversine formula implementation.
     */
    public static double calculateDistanceKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2.0) * Math.sin(dLat / 2.0)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2.0) * Math.sin(dLon / 2.0);

        double c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));

        return EARTH_RADIUS_KM * c;
    }
}
