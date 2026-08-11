package com.upsi.smartbus.model

import com.google.android.gms.maps.model.LatLng

/**
 * Represents a single navigation maneuver step from OSRM routing.
 * Used by DriverControlFragment to display Google Maps-style turn-by-turn instructions.
 */
data class NavStep(
    /** Maneuver type: "turn", "new name", "depart", "arrive", "merge", "fork", "roundabout", "continue" */
    val type: String,
    /** Direction modifier: "left", "right", "straight", "slight left", "slight right", "sharp left", "sharp right", "uturn" */
    val modifier: String,
    /** Distance of this step in meters */
    val distanceMeters: Double,
    /** Duration of this step in seconds */
    val durationSeconds: Double,
    /** Road / street name for this step */
    val roadName: String,
    /** Location where this maneuver occurs */
    val location: LatLng,
    /** Target stop abbreviation associated with this leg */
    val targetStopAbbr: String = ""
) {
    /** Human-readable instruction for display */
    fun getInstruction(nextStopFullName: String = ""): String {
        val destination = when {
            roadName.isNotEmpty() -> roadName
            nextStopFullName.isNotEmpty() -> nextStopFullName
            targetStopAbbr.isNotEmpty() -> targetStopAbbr
            else -> "Jalan Utama"
        }

        val dirAction = when (modifier) {
            "left"         -> "Belok Kiri"
            "right"        -> "Belok Kanan"
            "slight left"  -> "Belok Sedikit Kiri"
            "slight right" -> "Belok Sedikit Kanan"
            "sharp left"   -> "Belok Tajam Kiri"
            "sharp right"  -> "Belok Tajam Kanan"
            "uturn"        -> "Pusing Balik"
            "straight"     -> "Terus Lurus"
            else           -> "Terus Lurus"
        }

        return when (type) {
            "depart" -> "Bermula ke $destination"
            "arrive" -> "Tiba di $destination"
            "roundabout", "rotary" -> "Masuk Bulatan ke $destination"
            else -> "$dirAction ke $destination"
        }
    }

    /** Icon resource identifier suffix for matching drawable */
    fun getIconType(): String {
        return when {
            type == "arrive"                              -> "arrive"
            type == "depart"                              -> "straight"
            modifier.contains("left", ignoreCase = true)  -> "turn_left"
            modifier.contains("right", ignoreCase = true) -> "turn_right"
            modifier == "uturn"                            -> "uturn"
            else                                          -> "straight"
        }
    }

    /** Formatted distance string */
    fun getDistanceText(): String {
        return if (distanceMeters < 1000) {
            "${distanceMeters.toInt()} m"
        } else {
            "%.1f km".format(distanceMeters / 1000.0)
        }
    }
}
