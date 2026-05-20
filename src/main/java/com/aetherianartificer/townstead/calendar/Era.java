package com.aetherianartificer.townstead.calendar;

import net.minecraft.network.chat.Component;

/**
 * A named period in a {@link CalendarProfile}'s timeline. Eras let calendars
 * model "1E / 2E / 3E" (Tamriel), "BE" (Buddhist), "AH" (Hijri), Japanese
 * imperial eras (Reiwa, Heisei, …), and the BC half of BC/AD.
 *
 * <p><b>Ascending eras</b> (the common case): year counts up from
 * {@code startYear}. Displayed value =
 * {@code year - startYear + firstYearDisplayedAs}. So an era starting at
 * absolute year 3000 with {@code firstYearDisplayedAs=1} renders world
 * year 3499 as era-year 500.</p>
 *
 * <p><b>Descending eras</b> (BC-style pre-history): year counts down toward
 * the NEXT era's start. Displayed value =
 * {@code next.startYear - year - 1 + firstYearDisplayedAs}. With BC defined
 * as {@code (start=-32768, desc, first=1)} followed by AD
 * {@code (start=1, asc)}, world year 0 renders as "1 BC", world year -2025
 * renders as "2026 BC", year 2026 (post-boundary) renders as "2026 AD".</p>
 *
 * <p>A descending era REQUIRES a successor era (to know its end-boundary).
 * If the last era is descending, the profile loader treats it as ascending
 * to avoid undefined math.</p>
 *
 * <p>{@code firstYearDisplayedAs} defaults to 1 for most calendars. Some
 * (e.g., Tamriel canon counting from 0) use 0.</p>
 */
public record Era(Component name, int startYear, int firstYearDisplayedAs, Direction direction) {

    public enum Direction { ASCENDING, DESCENDING }

    /** Pure-data view of a date within a resolved era. */
    public record Resolved(Era era, int displayedYear) {}

    /** Default convention: ascending, years count from 1. */
    public static Era of(Component name, int startYear) {
        return new Era(name, startYear, 1, Direction.ASCENDING);
    }

    /** Backwards-compatible constructor for callers from before {@link Direction} existed. */
    public Era(Component name, int startYear, int firstYearDisplayedAs) {
        this(name, startYear, firstYearDisplayedAs, Direction.ASCENDING);
    }
}
