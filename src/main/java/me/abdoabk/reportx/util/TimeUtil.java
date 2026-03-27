package me.abdoabk.reportx.util;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public class TimeUtil {

    public static String formatTimeAgo(LocalDateTime dateTime) {
        if (dateTime == null) return "Unknown";
        LocalDateTime now = LocalDateTime.now();
        long seconds = ChronoUnit.SECONDS.between(dateTime, now);

        if (seconds < 60) return seconds + "s ago";
        long minutes = ChronoUnit.MINUTES.between(dateTime, now);
        if (minutes < 60) return minutes + "m ago";
        long hours = ChronoUnit.HOURS.between(dateTime, now);
        if (hours < 24) return hours + "h ago";
        long days = ChronoUnit.DAYS.between(dateTime, now);
        if (days < 30) return days + "d ago";
        long months = days / 30;
        return months + "mo ago";
    }

    public static String formatSeconds(long seconds) {
        if (seconds < 60) return seconds + " second" + (seconds != 1 ? "s" : "");
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + " minute" + (minutes != 1 ? "s" : "");
        long hours = minutes / 60;
        return hours + " hour" + (hours != 1 ? "s" : "");
    }

    public static String formatHours(double hours) {
        if (hours < 1) return (int)(hours * 60) + " minutes";
        if (hours < 24) return String.format("%.1f hours", hours);
        return String.format("%.1f days", hours / 24);
    }
}
