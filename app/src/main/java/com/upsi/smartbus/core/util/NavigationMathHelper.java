package com.upsi.smartbus.core.util;

/**
 * Geometric and Navigation Maneuver Calculation Engine (Implemented in Java).
 * Calculates relative angles, turn directions, and maneuver instructions for the driver navigation HUD.
 */
public class NavigationMathHelper {

    public enum ManeuverType {
        STRAIGHT,
        TURN_RIGHT,
        TURN_LEFT,
        U_TURN,
        ARRIVED
    }

    public static class NavigationInstruction {
        public final ManeuverType type;
        public final String instruction;
        public final double relativeAngle;

        public NavigationInstruction(ManeuverType type, String instruction, double relativeAngle) {
            this.type = type;
            this.instruction = instruction;
            this.relativeAngle = relativeAngle;
        }
    }

    /**
     * Calculates the relative angle in degrees between the current position and target stop.
     *
     * @param currentLat Current latitude
     * @param currentLon Current longitude
     * @param targetLat Target stop latitude
     * @param targetLon Target stop longitude
     * @return Angle in degrees between -180.0 and +180.0
     */
    public static double calculateRelativeAngle(
            double currentLat, double currentLon,
            double targetLat, double targetLon
    ) {
        double lat1 = Math.toRadians(currentLat);
        double lon1 = Math.toRadians(currentLon);
        double lat2 = Math.toRadians(targetLat);
        double lon2 = Math.toRadians(targetLon);

        double dLon = lon2 - lon1;
        double y = Math.sin(dLon) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2) -
                Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon);

        double bearing = Math.toDegrees(Math.atan2(y, x));
        bearing = (bearing + 360.0) % 360.0;
        if (bearing > 180.0) {
            bearing -= 360.0;
        }
        return bearing;
    }

    /**
     * Determines navigation maneuver and generates localized instruction.
     *
     * @param currentLat Current latitude
     * @param currentLon Current longitude
     * @param targetLat Target latitude
     * @param targetLon Target longitude
     * @param distanceKm Distance remaining in kilometers
     * @param targetStopName Full name or abbreviation of target stop
     * @return NavigationInstruction object
     */
    public static NavigationInstruction determineManeuver(
            double currentLat, double currentLon,
            double targetLat, double targetLon,
            double distanceKm, String targetStopName
    ) {
        if (distanceKm < 0.035) { // Within 35m
            return new NavigationInstruction(
                    ManeuverType.ARRIVED,
                    "Tiba di " + targetStopName,
                    0.0
            );
        }

        double relativeAngle = calculateRelativeAngle(currentLat, currentLon, targetLat, targetLon);

        if (relativeAngle >= -25.0 && relativeAngle <= 25.0) {
            return new NavigationInstruction(
                    ManeuverType.STRAIGHT,
                    "Terus Lurus ke " + targetStopName,
                    relativeAngle
            );
        } else if (relativeAngle > 25.0 && relativeAngle <= 135.0) {
            return new NavigationInstruction(
                    ManeuverType.TURN_RIGHT,
                    "Belok Kanan ke " + targetStopName,
                    relativeAngle
            );
        } else if (relativeAngle < -25.0 && relativeAngle >= -135.0) {
            return new NavigationInstruction(
                    ManeuverType.TURN_LEFT,
                    "Belok Kiri ke " + targetStopName,
                    relativeAngle
            );
        } else {
            return new NavigationInstruction(
                    ManeuverType.U_TURN,
                    "Pusing Balik ke " + targetStopName,
                    relativeAngle
            );
        }
    }
}
