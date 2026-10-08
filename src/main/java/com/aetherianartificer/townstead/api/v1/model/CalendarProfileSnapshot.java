package com.aetherianartificer.townstead.api.v1.model;

import java.util.List;

/** The shape of the active calendar profile for the given year. */
public record CalendarProfileSnapshot(
        String id,
        int year,
        List<MonthInfo> months,
        List<WeekdayInfo> weekdays,
        int daysInYear,
        boolean hasSeasons
) {
    public CalendarProfileSnapshot {
        months = months == null ? List.of() : List.copyOf(months);
        weekdays = weekdays == null ? List.of() : List.copyOf(weekdays);
    }

    /** A month; {@code index} is 1-based; a one-day month is a festival. */
    public record MonthInfo(int index, String name, int days, boolean intercalary) {
    }

    /** A weekday; {@code index} is 0-based. */
    public record WeekdayInfo(int index, String name, String shortName) {
    }
}
