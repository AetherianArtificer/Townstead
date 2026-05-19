package com.aetherianartificer.townstead.calendar;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Client-side mirror of the server's calendar state. Updated by
 * {@link CalendarSyncPayload} handlers, cleared on logout.
 *
 * Text fields arrive as (translate key, fallback) pairs and are reconstructed
 * into Components on the client so each player sees their own locale.
 *
 * Profile-shape fields ({@code daysPerWeek}, {@code months}, {@code yearSuffix},
 * {@code epochYearOffset}) let the calendar UI render arbitrary months/years
 * without a round-trip per navigation.
 */
public final class CalendarClientStore {

    public record MonthSpec(String key, String fallback, int days) {
        public Component nameComponent() { return ComponentSync.reconstruct(key, fallback); }
    }

    public record WeekdaySpec(String longKey, String longFallback, String shortKey, String shortFallback) {
        public Component longComponent()  { return ComponentSync.reconstruct(longKey, longFallback); }
        public Component shortComponent() { return ComponentSync.reconstruct(shortKey, shortFallback); }
    }

    public record EraSpec(String nameKey, String nameFallback, int startYear, int firstYearDisplayedAs, int direction) {
        public Component nameComponent() { return ComponentSync.reconstruct(nameKey, nameFallback); }
        public boolean isDescending() { return direction == 1; }
    }

    public record EraResolved(EraSpec era, int displayedYear) {
        public Component nameComponent() { return era.nameComponent(); }
    }

    public record Snapshot(
            long worldDay,
            int year,
            int monthIndex,
            int dayOfMonth,
            int dayOfYear,
            int dayOfWeek,
            String monthKey,
            String monthFallback,
            String profileKey,
            String profileFallback,
            String seasonKey,
            int daysPerWeek,
            int epochYearOffset,
            String yearSuffixKey,
            String yearSuffixFallback,
            List<MonthSpec> months,
            List<WeekdaySpec> weekdays,
            List<EraSpec> eras,
            List<LeapRule> leapRules
    ) {

        public Component monthComponent() { return ComponentSync.reconstruct(monthKey, monthFallback); }
        public Component profileComponent() { return ComponentSync.reconstruct(profileKey, profileFallback); }
        public Component yearSuffixComponent() { return ComponentSync.reconstruct(yearSuffixKey, yearSuffixFallback); }
        public Component seasonComponent() {
            if (seasonKey == null || seasonKey.isEmpty()) return Component.empty();
            return Component.translatable(seasonKey);
        }
        public boolean hasSeason() { return seasonKey != null && !seasonKey.isEmpty(); }
        public boolean hasYearSuffix() {
            return (yearSuffixKey != null && !yearSuffixKey.isEmpty())
                    || (yearSuffixFallback != null && !yearSuffixFallback.isEmpty());
        }
        /**
         * Nominal days per year (sum of base months). Same as the server's
         * {@code CalendarProfile.daysPerYear()} — ignores leap rules. Use
         * {@link #daysInYear(int)} for leap-aware year length.
         */
        public int daysPerYear() {
            int total = 0;
            for (MonthSpec m : months) total += m.days();
            return total;
        }

        public boolean hasWeekdays() { return weekdays != null && !weekdays.isEmpty(); }
        public boolean hasEras() { return eras != null && !eras.isEmpty(); }
        public boolean hasLeapRules() { return leapRules != null && !leapRules.isEmpty(); }

        /**
         * Convert this snapshot's month specs into engine {@link MonthDef}s
         * so the client can use {@link LeapEngine}. Memoized by snapshot
         * identity in {@link CalendarClientStore#engineMonthsFor} since
         * records can't hold mutable cache fields and rebuilding is cheap
         * but not free.
         */
        public List<MonthDef> engineMonths() {
            return engineMonthsFor(this);
        }

        /** Days in {@code year}, accounting for any leap rules. */
        public int daysInYear(int year) {
            return LeapEngine.daysInYear(engineMonths(), leapRules, year);
        }

        /** Days in the given 1-based month of {@code year}. */
        public int daysInMonth(int year, int monthIndex) {
            return LeapEngine.daysInMonth(engineMonths(), leapRules, year, monthIndex);
        }

        /** Effective month list for {@code year} (with leap insertions). */
        public List<MonthDef> monthsForYear(int year) {
            return LeapEngine.layoutForYear(engineMonths(), leapRules, year).months();
        }

        /** Days in the year before the start of 1-based {@code monthIndex}. */
        public int daysBeforeMonth(int year, int monthIndex) {
            return LeapEngine.daysBeforeMonth(engineMonths(), leapRules, year, monthIndex);
        }

        /** Absolute worldDay at the start of the given display year. */
        public long worldDayAtYearStart(int displayYear) {
            return LeapEngine.worldDayAtYearStart(engineMonths(), leapRules, displayYear, epochYearOffset);
        }

        /**
         * Find which era an absolute year belongs to and what era-relative
         * year to display. Mirrors {@link CalendarProfile#resolveEra}.
         * Returns null if no eras defined.
         */
        @org.jetbrains.annotations.Nullable
        public EraResolved resolveEra(int year) {
            if (!hasEras()) return null;
            int chosenIdx = 0;
            for (int i = 0; i < eras.size(); i++) {
                if (eras.get(i).startYear() <= year) chosenIdx = i;
                else break;
            }
            EraSpec chosen = eras.get(chosenIdx);
            EraSpec next = (chosenIdx + 1 < eras.size()) ? eras.get(chosenIdx + 1) : null;
            int displayed;
            if (chosen.isDescending() && next != null) {
                displayed = next.startYear() - year - 1 + chosen.firstYearDisplayedAs();
            } else {
                displayed = year - chosen.startYear() + chosen.firstYearDisplayedAs();
            }
            return new EraResolved(chosen, displayed);
        }
    }

    private static volatile @Nullable Snapshot current;

    // Identity-keyed weak cache so a long-lived snapshot doesn't rebuild the
    // engine month list on every UI tick. Dropped when the snapshot itself
    // becomes unreferenced.
    private static final java.util.Map<Snapshot, List<MonthDef>> ENGINE_MONTHS_CACHE =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    static List<MonthDef> engineMonthsFor(Snapshot snap) {
        List<MonthDef> cached = ENGINE_MONTHS_CACHE.get(snap);
        if (cached != null) return cached;
        List<MonthDef> built = new java.util.ArrayList<>(snap.months().size());
        for (MonthSpec m : snap.months()) built.add(new MonthDef(m.nameComponent(), m.days()));
        cached = List.copyOf(built);
        ENGINE_MONTHS_CACHE.put(snap, cached);
        return cached;
    }

    private CalendarClientStore() {}

    public static void setFrom(CalendarSyncPayload payload) {
        java.util.List<MonthSpec> months = new java.util.ArrayList<>(payload.monthKeys().size());
        for (int i = 0; i < payload.monthKeys().size(); i++) {
            months.add(new MonthSpec(
                    payload.monthKeys().get(i),
                    payload.monthFallbacks().get(i),
                    payload.monthDays().get(i)));
        }
        java.util.List<WeekdaySpec> weekdays = new java.util.ArrayList<>(payload.weekdayLongKeys().size());
        for (int i = 0; i < payload.weekdayLongKeys().size(); i++) {
            weekdays.add(new WeekdaySpec(
                    payload.weekdayLongKeys().get(i),
                    payload.weekdayLongFallbacks().get(i),
                    payload.weekdayShortKeys().get(i),
                    payload.weekdayShortFallbacks().get(i)));
        }
        java.util.List<EraSpec> eras = new java.util.ArrayList<>(payload.eraNameKeys().size());
        for (int i = 0; i < payload.eraNameKeys().size(); i++) {
            eras.add(new EraSpec(
                    payload.eraNameKeys().get(i),
                    payload.eraNameFallbacks().get(i),
                    payload.eraStartYears().get(i),
                    payload.eraFirstYearDisplayedAs().get(i),
                    payload.eraDirections().get(i)));
        }
        current = new Snapshot(
                payload.worldDay(),
                payload.year(),
                payload.monthIndex(),
                payload.dayOfMonth(),
                payload.dayOfYear(),
                payload.dayOfWeek(),
                payload.monthKey(),
                payload.monthFallback(),
                payload.profileKey(),
                payload.profileFallback(),
                payload.seasonKey(),
                payload.daysPerWeek(),
                payload.epochYearOffset(),
                payload.yearSuffixKey(),
                payload.yearSuffixFallback(),
                months,
                weekdays,
                eras,
                payload.leapRules() != null ? payload.leapRules() : List.of()
        );
    }

    @Nullable
    public static Snapshot get() {
        return current;
    }

    public static void clear() {
        current = null;
    }
}
