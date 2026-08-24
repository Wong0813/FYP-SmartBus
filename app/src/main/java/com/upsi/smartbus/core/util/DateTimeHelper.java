package com.upsi.smartbus.core.util;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Common Date and Time utility methods implemented in Java.
 * Used across Kotlin Fragments and Activities to demonstrate Java-Kotlin Interoperability.
 */
public class DateTimeHelper {

    private static final SimpleDateFormat HEADER_DATE_FORMAT =
            new SimpleDateFormat("EEEE, d MMMM yyyy", Locale.ENGLISH);

    private static final SimpleDateFormat TIME_FORMAT =
            new SimpleDateFormat("hh:mm a", Locale.ENGLISH);

    /**
     * Formats current date for Dashboard Hero Header (e.g. "Monday, 24 August 2026").
     */
    public static String getFormattedCurrentDate() {
        return HEADER_DATE_FORMAT.format(new Date());
    }

    /**
     * Formats a timestamp into human-readable 12-hour time (e.g. "08:30 PM").
     */
    public static String formatTimestamp(long timestamp) {
        if (timestamp <= 0) return "--:--";
        return TIME_FORMAT.format(new Date(timestamp));
    }

    /**
     * Determines whether today is a Weekday, Saturday, or Sunday.
     */
    public static String getTodayScheduleType() {
        Calendar cal = Calendar.getInstance();
        int day = cal.get(Calendar.DAY_OF_WEEK);
        if (day == Calendar.SUNDAY) {
            return "NO SERVICE";
        } else if (day == Calendar.SATURDAY) {
            return "SATURDAY";
        } else {
            return "WEEKDAY";
        }
    }
}
