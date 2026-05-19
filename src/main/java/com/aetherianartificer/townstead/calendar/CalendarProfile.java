package com.aetherianartificer.townstead.calendar;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Data-pack-loaded calendar profile. Carries only structural calendar data
 * (months, days per week, display name, optional year suffix, optional
 * weekday names, optional eras, optional format overrides).
 *
 * <p>Constructed by {@link CalendarProfileJsonLoader} from
 * {@code data/<ns>/calendar_profile/<path>.json}.</p>
 *
 * <p><b>No math driver field.</b> The time source that advances the day
 * counter is a server-config concern ({@code townstead.calendar.dayDriver})
 * — pack authors describe shape only. Seasonal-bridge profiles
 * ({@code townstead_calendar:serene}, {@code :tfc}, {@code :ecliptic}) are
 * mod-internal; their drivers are hard-coded in {@link CalendarTypes#resolveDriverFor}.</p>
 *
 * <p>{@code yearSuffix} is nullable; when null, callers that need a string
 * suffix should fall back to empty. Surfaced anywhere the displayed year
 * benefits from an era label in a HUD or chronicle entry; profiles set it
 * via the optional {@code year_suffix} JSON field (translatable + fallback)
 * or runtime synthesis.</p>
 *
 * <p>{@code weekdays} is also optional. When provided, it must have length
 * equal to {@code daysPerWeek}. When null, the calendar UI uses numeric
 * column headers (1, 2, 3…) and date formatters omit weekday segments.</p>
 *
 * <p>{@code formats} is optional and maps zero or more {@link CalendarDateFormatter.Style}
 * values to a Component carrying the format pattern. When a style is missing
 * from the map, {@link CalendarDateFormatter} falls back to the global
 * {@code townstead.calendar.format.<style>} key.</p>
 */
public record CalendarProfile(
        ResourceLocation id,
        Component displayName,
        int daysPerWeek,
        List<MonthDef> months,
        @Nullable Component yearSuffix,
        @Nullable List<WeekdayDef> weekdays,
        @Nullable List<Era> eras,
        @Nullable Map<CalendarDateFormatter.Style, Component> formats,
        @Nullable List<LeapRule> leapRules
) {
    public CalendarProfile {
        if (daysPerWeek <= 0) throw new IllegalArgumentException("daysPerWeek must be > 0");
        if (months == null) throw new IllegalArgumentException("months must not be null");
        months = List.copyOf(months);
        if (weekdays != null) {
            if (weekdays.size() != daysPerWeek) {
                throw new IllegalArgumentException("weekdays length (" + weekdays.size()
                        + ") must equal daysPerWeek (" + daysPerWeek + ")");
            }
            weekdays = List.copyOf(weekdays);
        }
        if (eras != null) {
            java.util.List<Era> sorted = new java.util.ArrayList<>(eras);
            sorted.sort(java.util.Comparator.comparingInt(Era::startYear));
            eras = List.copyOf(sorted);
        }
        if (formats != null && formats.isEmpty()) {
            formats = null;
        } else if (formats != null) {
            EnumMap<CalendarDateFormatter.Style, Component> copy =
                    new EnumMap<>(CalendarDateFormatter.Style.class);
            copy.putAll(formats);
            formats = java.util.Collections.unmodifiableMap(copy);
        }
        if (leapRules != null && leapRules.isEmpty()) {
            leapRules = null;
        } else if (leapRules != null) {
            leapRules = List.copyOf(leapRules);
        }
    }

    /** Backwards-compatible constructor: no suffix, no weekdays, no eras, no formats, no leap rules. */
    public CalendarProfile(
            ResourceLocation id,
            Component displayName,
            int daysPerWeek,
            List<MonthDef> months
    ) {
        this(id, displayName, daysPerWeek, months, null, null, null, null, null);
    }

    /** Backwards-compatible constructor: with suffix, no weekdays, no eras, no formats, no leap rules. */
    public CalendarProfile(
            ResourceLocation id,
            Component displayName,
            int daysPerWeek,
            List<MonthDef> months,
            @Nullable Component yearSuffix
    ) {
        this(id, displayName, daysPerWeek, months, yearSuffix, null, null, null, null);
    }

    /** Backwards-compatible constructor: no eras, no formats, no leap rules. */
    public CalendarProfile(
            ResourceLocation id,
            Component displayName,
            int daysPerWeek,
            List<MonthDef> months,
            @Nullable Component yearSuffix,
            @Nullable List<WeekdayDef> weekdays
    ) {
        this(id, displayName, daysPerWeek, months, yearSuffix, weekdays, null, null, null);
    }

    /** Backwards-compatible constructor: no formats, no leap rules. */
    public CalendarProfile(
            ResourceLocation id,
            Component displayName,
            int daysPerWeek,
            List<MonthDef> months,
            @Nullable Component yearSuffix,
            @Nullable List<WeekdayDef> weekdays,
            @Nullable List<Era> eras
    ) {
        this(id, displayName, daysPerWeek, months, yearSuffix, weekdays, eras, null, null);
    }

    /** Backwards-compatible constructor: no leap rules. */
    public CalendarProfile(
            ResourceLocation id,
            Component displayName,
            int daysPerWeek,
            List<MonthDef> months,
            @Nullable Component yearSuffix,
            @Nullable List<WeekdayDef> weekdays,
            @Nullable List<Era> eras,
            @Nullable Map<CalendarDateFormatter.Style, Component> formats
    ) {
        this(id, displayName, daysPerWeek, months, yearSuffix, weekdays, eras, formats, null);
    }

    /**
     * Look up an optional per-profile format override for the given style.
     * Returns null when no override is defined for that style or no formats
     * are configured at all; the caller should fall back to the global
     * {@code townstead.calendar.format.<style>} translation key.
     */
    @Nullable
    public Component format(CalendarDateFormatter.Style style) {
        return formats == null ? null : formats.get(style);
    }

    /**
     * Find the era an absolute year belongs to, and the era-relative
     * displayed year. Returns null when this profile has no eras defined.
     *
     * Algorithm: pick the era with the highest {@code startYear <= year}
     * (or the first era if {@code year} predates all of them). Ascending
     * eras count up from their start; descending eras count down toward
     * the successor era's start. A descending era at the end of the list
     * (no successor) falls back to ascending behavior to avoid undefined
     * math.
     */
    @Nullable
    public Era.Resolved resolveEra(int year) {
        if (eras == null || eras.isEmpty()) return null;
        int chosenIdx = 0;
        for (int i = 0; i < eras.size(); i++) {
            if (eras.get(i).startYear() <= year) chosenIdx = i;
            else break;
        }
        Era chosen = eras.get(chosenIdx);
        Era next = (chosenIdx + 1 < eras.size()) ? eras.get(chosenIdx + 1) : null;

        int displayed;
        if (chosen.direction() == Era.Direction.DESCENDING && next != null) {
            displayed = next.startYear() - year - 1 + chosen.firstYearDisplayedAs();
        } else {
            displayed = year - chosen.startYear() + chosen.firstYearDisplayedAs();
        }
        return new Era.Resolved(chosen, displayed);
    }

    /**
     * Nominal year length: sum of {@code months[].days} on the base month
     * list, ignoring any {@link LeapRule}s. Suitable for randomization seeds
     * and DOB fabrication where ~1-day-per-cycle drift is acceptable. For
     * exact year math, prefer {@link #daysInYear(int)}.
     */
    public int daysPerYear() {
        int total = 0;
        for (MonthDef m : months) total += m.days();
        return total;
    }

    /**
     * Days in the given absolute {@code year}, accounting for leap rules.
     * Equal to {@link #daysPerYear()} when no leap rules are defined.
     */
    public int daysInYear(int year) {
        return LeapEngine.daysInYear(months, leapRules, year);
    }

    /**
     * Days in the given 1-based month of {@code year}. {@code monthIndex}
     * indexes into the post-leap-rule month list (so leap years that insert
     * a 13th month make 13 a valid index for those years).
     */
    public int daysInMonth(int year, int monthIndex) {
        return LeapEngine.daysInMonth(months, leapRules, year, monthIndex);
    }

    /**
     * The effective month list for {@code year}. With no leap rules, returns
     * the base list. With leap rules, returns a fresh list with day-count
     * adjustments and inserted months applied for that year.
     */
    public List<MonthDef> monthsForYear(int year) {
        return LeapEngine.layoutForYear(months, leapRules, year).months();
    }
}
