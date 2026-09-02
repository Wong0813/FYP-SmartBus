package com.upsi.smartbus.core.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Standard ETA, Distance Formatting, and Arrival Clock Calculation Engine (Implemented in Java).
 * Calculates realistic travel duration, arrival timestamps, and human-friendly distance strings.
 */
public class BusEtaCalculator {

    private static final double DEFAULT_CAMPUS_SPEED_KMH = 30.0;
    private static final double ARRIVAL_THRESHOLD_KM = 0.035; // 35 meters threshold

    /**
     * Calculates standard physics-based ETA in minutes from distance and speed.
     *
     * @param distanceKm Distance remaining to destination in kilometers
     * @param speedKmh Current speed in km/h
     * @return Estimated minutes remaining (0 if arrived, minimum 1 minute if in transit)
     */
    public static int calculateStandardEtaMinutes(double distanceKm, double speedKmh) {
        if (distanceKm <= ARRIVAL_THRESHOLD_KM) {
            return 0; // Arrived
        }

        double effectiveSpeed = speedKmh;
        // If speed is zero or too low (e.g. stopped at bus stop/red light), use realistic campus cruising speed
        if (effectiveSpeed < 10.0) {
            effectiveSpeed = DEFAULT_CAMPUS_SPEED_KMH;
        }

        // time in hours = distance / speed
        // time in minutes = (distance / speed) * 60
        double minutes = (distanceKm / effectiveSpeed) * 60.0;
        return (int) Math.max(1, Math.round(minutes));
    }

    /**
     * Calculates the estimated clock arrival time (e.g. "14:35") given the ETA duration in minutes.
     *
     * @param etaMinutes Minutes until arrival
     * @return 24-hour formatted arrival time "HH:mm"
     */
    public static String calculateArrivalClock(int etaMinutes) {
        return calculateArrivalClock(System.currentTimeMillis(), etaMinutes);
    }

    /**
     * Calculates the estimated clock arrival time from a specific start timestamp.
     *
     * @param baseTimestampMs Base timestamp in milliseconds
     * @param etaMinutes Minutes to add
     * @return Formatted clock time "HH:mm"
     */
    public static String calculateArrivalClock(long baseTimestampMs, int etaMinutes) {
        long targetMs = baseTimestampMs + (etaMinutes * 60L * 1000L);
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        return sdf.format(new Date(targetMs));
    }

    /**
     * Formats distance in a human-friendly format:
     * - Under 1.0 km -> "350 m"
     * - 1.0 km and above -> "1.2 km"
     *
     * @param distanceKm Distance in kilometers
     * @return Formatted distance string
     */
    public static String formatDistance(double distanceKm) {
        if (distanceKm <= 0.0) {
            return "0 m";
        }
        if (distanceKm < 1.0) {
            int meters = (int) Math.round(distanceKm * 1000.0);
            return meters + " m";
        } else {
            return String.format(Locale.ENGLISH, "%.1f km", distanceKm);
        }
    }

    /**
     * Formats ETA minutes for UI badge display (e.g. "~4 min", "< 1 min", "Arrived").
     *
     * @param etaMinutes Estimated arrival minutes
     * @return Formatted string for UI
     */
    public static String formatEtaBadge(int etaMinutes) {
        if (etaMinutes <= 0) {
            return "Arrived";
        } else if (etaMinutes == 1) {
            return "~1 min";
        } else {
            return "~" + etaMinutes + " min";
        }
    }
}
