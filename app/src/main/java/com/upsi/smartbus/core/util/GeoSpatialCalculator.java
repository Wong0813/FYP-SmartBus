package com.upsi.smartbus.core.util;

/**
 * Geo-Spatial and Geometric Navigation Engine (Implemented in Java).
 * Handles GPS coordinate math, bearing/heading calculations, and geofence detection.
 */
public class GeoSpatialCalculator {

    private static final double EARTH_RADIUS_METERS = 6371000.0;

    /**
     * Calculates the forward bearing (heading angle) from point A to point B in degrees (0 - 360).
     * Used to rotate bus markers smoothly on Google Maps in the driving direction.
     *
     * @param lat1 Starting latitude in degrees
     * @param lon1 Starting longitude in degrees
     * @param lat2 Destination latitude in degrees
     * @param lon2 Destination longitude in degrees
     * @return Bearing in degrees from 0.0 to 360.0 (clockwise from True North)
     */
    public static float calculateBearing(double lat1, double lon1, double lat2, double lon2) {
        if (Math.abs(lat1 - lat2) < 0.000001 && Math.abs(lon1 - lon2) < 0.000001) {
            return 0.0f;
        }

        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double deltaLambda = Math.toRadians(lon2 - lon1);

        double y = Math.sin(deltaLambda) * Math.cos(phi2);
        double x = Math.cos(phi1) * Math.sin(phi2) -
                Math.sin(phi1) * Math.cos(phi2) * Math.cos(deltaLambda);

        double theta = Math.atan2(y, x);
        double bearing = (Math.toDegrees(theta) + 360.0) % 360.0;

        return (float) bearing;
    }

    /**
     * Calculates the great-circle distance between two GPS coordinates in meters.
     *
     * @param lat1 Latitude 1
     * @param lon1 Longitude 1
     * @param lat2 Latitude 2
     * @param lon2 Longitude 2
     * @return Distance in meters
     */
    public static double calculateDistanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2.0) * Math.sin(dLat / 2.0)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2.0) * Math.sin(dLon / 2.0);

        double c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));

        return EARTH_RADIUS_METERS * c;
    }

    /**
     * Determines if a bus has arrived inside a bus stop geofence.
     *
     * @param busLat Current bus latitude
     * @param busLon Current bus longitude
     * @param stopLat Bus stop latitude
     * @param stopLon Bus stop longitude
     * @param radiusMeters Geofence radius in meters (e.g. 35.0 meters)
     * @return true if the bus is inside the stop zone, false otherwise
     */
    public static boolean isInsideGeofence(double busLat, double busLon, double stopLat, double stopLon, double radiusMeters) {
        double dist = calculateDistanceMeters(busLat, busLon, stopLat, stopLon);
        return dist <= radiusMeters;
    }

    /**
     * Formats distance nicely for student UI.
     * e.g. "350 m" for < 1km, or "1.4 km" for >= 1km.
     */
    public static String formatDistance(double distanceKm) {
        if (distanceKm < 1.0) {
            int meters = (int) Math.round(distanceKm * 1000.0);
            return meters + " m";
        } else {
            return String.format(java.util.Locale.ENGLISH, "%.1f km", distanceKm);
        }
    }
}
