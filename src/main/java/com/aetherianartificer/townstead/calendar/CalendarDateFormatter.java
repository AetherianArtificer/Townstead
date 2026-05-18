package com.aetherianartificer.townstead.calendar;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

/**
 * Single source of truth for displaying Townstead calendar dates. Every
 * surface (villager DOB tooltip, calendar block, /townstead calendar get,
 * Timekeeper compat chat, etc.) routes through here so date format ordering
 * is fully controlled by the lang file via positional args.
 *
 * <h2>Format styles</h2>
 *
 * <ul>
 *   <li>{@link Style#LONG} — narrative form, e.g. "Day 17 of Melitar, 1234"
 *       (en_us renders as "Melitar 17, 1234")</li>
 *   <li>{@link Style#MEDIUM} — compact, e.g. "17 Melitar 1234"
 *       (en_us: "Melitar 17, 1234")</li>
 *   <li>{@link Style#SHORT} — numeric, e.g. "17/3/1234"
 *       (en_us: "3/17/1234")</li>
 *   <li>{@link Style#WITH_WEEKDAY} — like LONG but prefixed with the
 *       weekday name when the active profile defines weekdays</li>
 * </ul>
 *
 * <h2>Positional args (locale-controllable)</h2>
 *
 * Translation values get the same arg vector, in this fixed order:
 * <ol>
 *   <li>{@code %1$s} day-of-month (int)</li>
 *   <li>{@code %2$s} month name (Component)</li>
 *   <li>{@code %3$s} display year (int)</li>
 *   <li>{@code %4$s} weekday name (Component, only WITH_WEEKDAY)</li>
 *   <li>{@code %5$s} year suffix (Component; may be empty)</li>
 * </ol>
 * en_us reorders to month-first via {@code "%2$s %1$s, %3$s"}, other
 * locales pick their conventional ordering.
 */
public final class CalendarDateFormatter {
    private CalendarDateFormatter() {}

    public enum Style {
        LONG("townstead.calendar.format.long", "%2$s %1$s, %3$s"),
        MEDIUM("townstead.calendar.format.medium", "%2$s %1$s, %3$s"),
        SHORT("townstead.calendar.format.short", "%1$s/%6$s/%3$s"),
        WITH_WEEKDAY("townstead.calendar.format.with_weekday", "%4$s, %2$s %1$s, %3$s");

        final String key;
        final String fallback;
        Style(String key, String fallback) {
            this.key = key;
            this.fallback = fallback;
        }
    }

    /** Server-side: pulls active profile and worldDay through TownsteadCalendar. */
    public static Component format(MinecraftServer server, long worldDay, Style style) {
        CalendarDate date = TownsteadCalendar.dateOf(server, worldDay);
        if (date == CalendarDate.UNKNOWN) return Component.empty();
        CalendarProfile profile = TownsteadCalendar.activeProfile(server);
        if (profile == null) return Component.empty();
        Component month = monthName(profile, date.monthIndex());
        Component weekday = weekdayLong(profile, date.dayOfWeek());

        // Era resolution takes precedence over yearSuffix. If no era, fall
        // back to the (year, yearSuffix) pair the profile already exposes.
        int displayedYear = date.year();
        Component yearLabel;
        Era.Resolved resolved = profile.resolveEra(date.year());
        if (resolved != null) {
            displayedYear = resolved.displayedYear();
            yearLabel = resolved.era().name();
        } else {
            yearLabel = profile.yearSuffix() != null ? profile.yearSuffix() : Component.empty();
        }

        return Component.translatableWithFallback(style.key, style.fallback,
                date.dayOfMonth(), month, displayedYear, weekday, yearLabel, date.monthIndex());
    }

    /** Server-side convenience: format today's date. */
    public static Component formatToday(MinecraftServer server, Style style) {
        return format(server, TownsteadCalendar.worldDay(server), style);
    }

    /** Client-side: builds the format from a synced snapshot. */
    public static Component formatClient(CalendarClientStore.Snapshot snap, Style style,
                                          int year, int monthIndex, int dayOfMonth, int dayOfWeek) {
        if (snap == null) return Component.empty();
        Component month = clientMonthName(snap, monthIndex);
        Component weekday = clientWeekdayLong(snap, dayOfWeek);

        int displayedYear = year;
        Component yearLabel;
        CalendarClientStore.EraResolved resolved = snap.resolveEra(year);
        if (resolved != null) {
            displayedYear = resolved.displayedYear();
            yearLabel = resolved.nameComponent();
        } else {
            yearLabel = snap.hasYearSuffix() ? snap.yearSuffixComponent() : Component.empty();
        }

        return Component.translatableWithFallback(style.key, style.fallback,
                dayOfMonth, month, displayedYear, weekday, yearLabel, monthIndex);
    }

    private static Component monthName(CalendarProfile profile, int monthIndex) {
        if (monthIndex >= 1 && monthIndex <= profile.months().size()) {
            return profile.months().get(monthIndex - 1).commonName();
        }
        return Component.literal("Month " + monthIndex);
    }

    @Nullable
    private static Component weekdayLong(CalendarProfile profile, int dayOfWeek) {
        if (profile.weekdays() == null) return Component.empty();
        if (dayOfWeek < 0 || dayOfWeek >= profile.weekdays().size()) return Component.empty();
        return profile.weekdays().get(dayOfWeek).longName();
    }

    private static Component clientMonthName(CalendarClientStore.Snapshot snap, int monthIndex) {
        if (monthIndex >= 1 && monthIndex <= snap.months().size()) {
            return snap.months().get(monthIndex - 1).nameComponent();
        }
        return Component.literal("Month " + monthIndex);
    }

    private static Component clientWeekdayLong(CalendarClientStore.Snapshot snap, int dayOfWeek) {
        if (!snap.hasWeekdays()) return Component.empty();
        if (dayOfWeek < 0 || dayOfWeek >= snap.weekdays().size()) return Component.empty();
        return snap.weekdays().get(dayOfWeek).longComponent();
    }
}
