package com.upsi.smartbus.core.util;

/**
 * Pure Java utility for sorting and prioritizing fleet status.
 * Java utilities compile instantly with javac without kotlinc symbol inference overhead.
 */
public final class FleetPriorityHelper {

    private FleetPriorityHelper() {}

    /**
     * Priority tier:
     * 0 = Speeding (High Risk)
     * 1 = Active en route
     * 2 = Resting
     * 3 = Offline
     */
    public static int getStatusPriority(boolean isSpeeding, boolean isActive, boolean isResting) {
        if (isSpeeding) return 0;
        if (isActive) return 1;
        if (isResting) return 2;
        return 3;
    }

    /**
     * Compares two fleet items by status urgency first, then route order.
     */
    public static int compareFleet(
            boolean aSpeeding, boolean aActive, boolean aResting, int aRouteOrder,
            boolean bSpeeding, boolean bActive, boolean bResting, int bRouteOrder
    ) {
        int aP = getStatusPriority(aSpeeding, aActive, aResting);
        int bP = getStatusPriority(bSpeeding, bActive, bResting);
        if (aP != bP) {
            return Integer.compare(aP, bP);
        }
        return Integer.compare(aRouteOrder, bRouteOrder);
    }
}
