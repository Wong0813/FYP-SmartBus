package com.upsi.smartbus.core.model

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
    /** Human-readable instruction for display (Google Maps format) */
    fun getInstruction(nextStopFullName: String = ""): String {
        val hasRoad = roadName.isNotBlank()
        val dest = when {
            hasRoad -> " ke $roadName"
            type.equals("arrive", ignoreCase = true) && nextStopFullName.isNotBlank() -> " di $nextStopFullName"
            type.equals("depart", ignoreCase = true) && nextStopFullName.isNotBlank() -> " ke arah $nextStopFullName"
            else -> ""
        }

        val dirAction = when (modifier.lowercase()) {
            "left"         -> "Belok Kiri"
            "right"        -> "Belok Kanan"
            "slight left"  -> "Belok Sedikit Kiri"
            "slight right" -> "Belok Sedikit Kanan"
            "sharp left"   -> "Belok Tajam Kiri"
            "sharp right"  -> "Belok Tajam Kanan"
            "uturn"        -> "Pusing Balik"
            "straight"     -> "Terus Lurus"
            else           -> if (type.equals("turn", ignoreCase = true)) "Belok" else "Terus Lurus"
        }

        return when (type.lowercase()) {
            "arrive" -> if (nextStopFullName.isNotBlank()) "Tiba di $nextStopFullName" else "Tiba di Destinasi"
            "depart" -> if (hasRoad) "Mula ke $roadName" else "Mula perjalanan"
            "roundabout", "rotary" -> if (hasRoad) "Masuk bulatan ke $roadName" else "Masuk bulatan"
            "exit roundabout", "exit rotary" -> if (hasRoad) "Keluar bulatan ke $roadName" else "Keluar bulatan"
            "end of road" -> "$dirAction di hujung jalan$dest"
            "fork" -> "$dirAction di simpang$dest"
            else -> "$dirAction$dest"
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
