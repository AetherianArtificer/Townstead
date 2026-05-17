package com.aetherianartificer.townstead.calendar;

import org.jetbrains.annotations.Nullable;

/**
 * Display-side view of a date. All indices are 1-based for user display
 * except {@code dayOfWeek} which is 0-based (0 = first day of week per profile).
 *
 * {@code season} may be null for profiles that do not model seasons
 * (Localtime, stub profiles).
 */
public record CalendarDate(
        int year,
        int monthIndex,
        int dayOfMonth,
        int dayOfWeek,
        int dayOfYear,
        @Nullable Season season
) {
    public static final CalendarDate UNKNOWN = new CalendarDate(0, 1, 1, 0, 1, null);
}
