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
            List<EraSpec> eras
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
        /** Sum of all months' day counts — total days in a year. */
        public int daysPerYear() {
            int total = 0;
            for (MonthSpec m : months) total += m.days();
            return total;
        }

        public boolean hasWeekdays() { return weekdays != null && !weekdays.isEmpty(); }
        public boolean hasEras() { return eras != null && !eras.isEmpty(); }

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
                eras
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
