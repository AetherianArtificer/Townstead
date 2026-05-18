package com.aetherianartificer.townstead.calendar;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Data-pack-loaded calendar profile. Carries only structural calendar data
 * (months, days per week, display name, optional year suffix, optional
 * weekday names) plus a reference to a {@link CalendarType} that owns the
 * actual date-compute logic.
 *
 * Constructed by {@link CalendarProfileJsonLoader} from
 * {@code data/<ns>/calendar_profile/<path>.json}.
 *
 * {@code yearSuffix} is nullable; when null, callers that need a string
 * suffix should fall back to empty. Surfaced anywhere the displayed year
 * benefits from an era label in a HUD or chronicle entry; profiles set it
 * via the optional {@code year_suffix} JSON field (translatable + fallback)
 * or runtime synthesis.
 *
 * {@code weekdays} is also optional. When provided, it must have length
 * equal to {@code daysPerWeek}. When null, the calendar UI uses numeric
 * column headers (1, 2, 3…) and date formatters omit weekday segments.
 */
public record CalendarProfile(
        ResourceLocation id,
        Component displayName,
        ResourceLocation typeId,
        int daysPerWeek,
        List<MonthDef> months,
        @Nullable Component yearSuffix,
        @Nullable List<WeekdayDef> weekdays,
        @Nullable List<Era> eras
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
            // Sort defensively by startYear ascending so lookup can rely on order
            java.util.List<Era> sorted = new java.util.ArrayList<>(eras);
            sorted.sort(java.util.Comparator.comparingInt(Era::startYear));
            eras = List.copyOf(sorted);
        }
    }

    /** Backwards-compatible constructor: no suffix, no weekdays, no eras. */
    public CalendarProfile(
            ResourceLocation id,
            Component displayName,
            ResourceLocation typeId,
            int daysPerWeek,
            List<MonthDef> months
    ) {
        this(id, displayName, typeId, daysPerWeek, months, null, null, null);
    }

    /** Backwards-compatible constructor: with suffix, no weekdays, no eras. */
    public CalendarProfile(
            ResourceLocation id,
            Component displayName,
            ResourceLocation typeId,
            int daysPerWeek,
            List<MonthDef> months,
            @Nullable Component yearSuffix
    ) {
        this(id, displayName, typeId, daysPerWeek, months, yearSuffix, null, null);
    }

    /** Backwards-compatible constructor: no eras. */
    public CalendarProfile(
            ResourceLocation id,
            Component displayName,
            ResourceLocation typeId,
            int daysPerWeek,
            List<MonthDef> months,
            @Nullable Component yearSuffix,
            @Nullable List<WeekdayDef> weekdays
    ) {
        this(id, displayName, typeId, daysPerWeek, months, yearSuffix, weekdays, null);
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
            else break; // eras are sorted ascending
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

    /** Sum of {@code months[].days}. */
    public int daysPerYear() {
        int total = 0;
        for (MonthDef m : months) total += m.days();
        return total;
    }
}
