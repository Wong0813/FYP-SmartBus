package com.upsi.smartbus.feature.driver.control;

import com.upsi.smartbus.core.model.NavStep;
import com.upsi.smartbus.core.util.GeoSpatialCalculator;
import com.upsi.smartbus.core.util.NavigationMathHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * High-performance Turn-by-Turn Navigation & Guidance Engine (Implemented in Java).
 * Calculates real-time distance countdown to upcoming turns, direction icons,
 * and next maneuver previews like Google Maps.
 */
public class DriverNavEngine {

    public static class NavGuidanceResult {
        public final int updatedStepIndex;
        public final double distanceToTurnMeters;
        public final String distanceFormatted;
        public final String mainInstruction;
        public final String iconType;
        public final String secondaryInstruction;

        public NavGuidanceResult(
                int updatedStepIndex,
                double distanceToTurnMeters,
                String distanceFormatted,
                String mainInstruction,
                String iconType,
                String secondaryInstruction
        ) {
            this.updatedStepIndex = updatedStepIndex;
            this.distanceToTurnMeters = distanceToTurnMeters;
            this.distanceFormatted = distanceFormatted;
            this.mainInstruction = mainInstruction;
            this.iconType = iconType;
            this.secondaryInstruction = secondaryInstruction;
        }
    }

    /**
     * Filters navigation steps for the active destination stop.
     */
    public static List<NavStep> filterLegSteps(List<NavStep> allSteps, String targetStopAbbr) {
        if (allSteps == null || allSteps.isEmpty()) {
            return new ArrayList<>();
        }
        List<NavStep> filtered = new ArrayList<>();
        for (NavStep s : allSteps) {
            if (s.getTargetStopAbbr().equalsIgnoreCase(targetStopAbbr)) {
                filtered.add(s);
            }
        }
        return filtered.isEmpty() ? allSteps : filtered;
    }

    /**
     * Evaluates turn-by-turn guidance based on current bus GPS and upcoming road steps.
     */
    public static NavGuidanceResult evaluateGuidance(
            double currentLat, double currentLon,
            double targetLat, double targetLon,
            String targetFullName,
            double distanceToNextKm,
            List<NavStep> legSteps,
            int currentStepIndex
    ) {
        if (legSteps != null && !legSteps.isEmpty()) {
            double busDistToTarget = GeoSpatialCalculator.calculateDistanceMeters(
                    currentLat, currentLon, targetLat, targetLon
            );

            int stepIndex = Math.max(0, Math.min(currentStepIndex, legSteps.size() - 1));

            // Advance through completed steps monotonically
            while (stepIndex < legSteps.size() - 1) {
                NavStep step = legSteps.get(stepIndex);
                double d = GeoSpatialCalculator.calculateDistanceMeters(
                        currentLat, currentLon,
                        step.getLocation().latitude, step.getLocation().longitude
                );
                double stepDistToTarget = GeoSpatialCalculator.calculateDistanceMeters(
                        step.getLocation().latitude, step.getLocation().longitude,
                        targetLat, targetLon
                );

                boolean isDepartPassed = step.getType().equalsIgnoreCase("depart") && d > 20.0;
                boolean isManeuverReached = d <= 25.0;
                boolean isCloserToTarget = busDistToTarget < (stepDistToTarget - 60.0);

                if (isManeuverReached || isDepartPassed || isCloserToTarget) {
                    stepIndex++;
                } else {
                    break;
                }
            }

            NavStep activeStep = legSteps.get(stepIndex);
            double distToTurnMeters = GeoSpatialCalculator.calculateDistanceMeters(
                    currentLat, currentLon,
                    activeStep.getLocation().latitude, activeStep.getLocation().longitude
            );

            NavStep nextStepAfterActive = (stepIndex + 1 < legSteps.size()) ? legSteps.get(stepIndex + 1) : null;

            String distText = (distToTurnMeters < 1000.0)
                    ? String.format(Locale.ENGLISH, "%d m", Math.round(distToTurnMeters))
                    : String.format(Locale.ENGLISH, "%.1f km", distToTurnMeters / 1000.0);

            String mainInst = activeStep.getInstruction(targetFullName);
            String iconType = activeStep.getIconType();

            String subInst = (nextStepAfterActive != null)
                    ? "Kemudian: " + nextStepAfterActive.getInstruction(targetFullName)
                    : "Destinasi: " + targetFullName;

            return new NavGuidanceResult(stepIndex, distToTurnMeters, distText, mainInst, iconType, subInst);
        }

        // Fallback when road steps are still loading
        double fallbackDistMeters = distanceToNextKm * 1000.0;
        String distText = (fallbackDistMeters < 1000.0)
                ? String.format(Locale.ENGLISH, "%d m", Math.round(fallbackDistMeters))
                : String.format(Locale.ENGLISH, "%.1f km", distanceToNextKm);

        NavigationMathHelper.NavigationInstruction maneuver = NavigationMathHelper.determineManeuver(
                currentLat, currentLon, targetLat, targetLon, distanceToNextKm, targetFullName
        );

        String iconType;
        switch (maneuver.type) {
            case ARRIVED:
                iconType = "arrive";
                break;
            case TURN_LEFT:
                iconType = "turn_left";
                break;
            case TURN_RIGHT:
                iconType = "turn_right";
                break;
            case U_TURN:
                iconType = "uturn";
                break;
            default:
                iconType = "straight";
                break;
        }

        return new NavGuidanceResult(
                0, fallbackDistMeters, distText, maneuver.instruction, iconType, "Destinasi: " + targetFullName
        );
    }
}
