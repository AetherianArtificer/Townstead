package com.aetherianartificer.townstead.api.v1.model;

/**
 * One calendar day. {@link #month} and {@link #day} are 1-based; {@link #dayOfWeek} is 0-based.
 * {@link #season} is one of {@code spring}, {@code summer}, {@code autumn}, {@code winter}, or
 * empty for a profile without seasons. {@link #intercalary} marks a one-day month, which is how
 * Townstead profiles express festivals.
 */
public record CalendarSnapshot(
        String profileId,
        long worldDay,
        int epochYearOffset,
        String timeMode,
        int year,
        int month,
        int day,
        int dayOfYear,
        int dayOfWeek,
        String season,
        String monthName,
        int monthDays,
        boolean intercalary,
        String weekdayName,
        int daysInYear
) {
}
