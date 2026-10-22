package com.aetherianartificer.townstead.calendar;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.compat.calendar.CalendarCompat;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fits a custom calendar onto the season mod's year. Each month's {@code days} becomes a weight,
 * the year becomes exactly the provider's length, and the year starts where the provider's year
 * starts, so a winter month always lands in winter. Leap rules are set aside while stretched,
 * because the season mod's year has a fixed length.
 */
public final class CalendarStretch {
    private record Key(CalendarProfile profile, int yearDays) {}

    private static final Map<Key, CalendarProfile> CACHE = new LinkedHashMap<>();
    private static final Set<CalendarProfile> STRETCHED = Collections.newSetFromMap(new IdentityHashMap<>());

    private CalendarStretch() {}

    /** The profile to use: stretched when a season mod runs and the profile is not that mod's own. */
    @Nullable
    public static CalendarProfile apply(MinecraftServer server, @Nullable CalendarProfile profile) {
        if (profile == null || server == null || isSeasonModProfile(profile)) return profile;
        var clock = SeasonClock.current(server);
        if (clock.isEmpty()) return profile;
        int yearDays = clock.get().yearDays();
        if (yearDays < profile.months().size()) return profile;
        synchronized (CACHE) {
            return CACHE.computeIfAbsent(new Key(profile, yearDays), key -> {
                if (CACHE.size() >= 16) { CACHE.clear(); STRETCHED.clear(); }
                CalendarProfile stretched = stretch(profile, yearDays);
                STRETCHED.add(stretched);
                Townstead.LOGGER.info("[Calendar] Stretched profile {} onto the {}-day season year: months of {} days",
                        profile.id(), yearDays, stretched.months().stream().map(m -> Integer.toString(m.days())).toList());
                return stretched;
            });
        }
    }

    public static boolean isStretched(CalendarProfile profile) {
        synchronized (CACHE) {
            return STRETCHED.contains(profile);
        }
    }

    private static boolean isSeasonModProfile(CalendarProfile profile) {
        return profile.id().equals(CalendarCompat.sereneId()) || profile.id().equals(CalendarCompat.tfcId())
                || profile.id().equals(CalendarCompat.eclipticId());
    }

    private static CalendarProfile stretch(CalendarProfile profile, int yearDays) {
        int[] weights = profile.months().stream().mapToInt(MonthDef::days).toArray();
        int[] lengths = lengths(weights, yearDays);
        List<MonthDef> months = new ArrayList<>(lengths.length);
        for (int i = 0; i < lengths.length; i++) months.add(new MonthDef(profile.months().get(i).commonName(), lengths[i]));
        return new CalendarProfile(profile.id(), profile.displayName(), profile.daysPerWeek(), months,
                profile.yearSuffix(), profile.weekdays(), profile.eras(), profile.formats(), null);
    }

    /**
     * Month lengths that keep the weights' proportions and sum to {@code yearDays}. Every month
     * keeps at least one day; leftover days go to the heaviest months first.
     */
    static int[] lengths(int[] weights, int yearDays) {
        int n = weights.length;
        int[] lengths = new int[n];
        if (n == 0) return lengths;
        long total = 0;
        for (int w : weights) total += Math.max(1, w);
        int assigned = 0;
        for (int i = 0; i < n; i++) {
            lengths[i] = Math.max(1, (int) (Math.max(1, weights[i]) * (long) yearDays / total));
            assigned += lengths[i];
        }
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) order[i] = i;
        java.util.Arrays.sort(order, (a, b) -> weights[b] != weights[a] ? Integer.compare(weights[b], weights[a]) : Integer.compare(a, b));
        for (int k = 0; assigned < yearDays; k = (k + 1) % n) { lengths[order[k]]++; assigned++; }
        for (int k = n - 1; assigned > yearDays; k = (k - 1 + n) % n) {
            if (lengths[order[k]] > 1) { lengths[order[k]]--; assigned--; }
        }
        return lengths;
    }

    /** Days to add to the world day so that today falls on the provider's day of its year. */
    static long alignShift(long worldDayToday, double phase, int yearDays) {
        if (yearDays <= 0) return 0;
        long providerDay = (long) Math.floor(phase * yearDays);
        return Math.floorMod(providerDay - Math.floorMod(worldDayToday, (long) yearDays), (long) yearDays);
    }

    /** The shift for a stretched profile today; zero for any other profile or without a clock. */
    public static long shift(MinecraftServer server, CalendarProfile profile) {
        if (server == null || !isStretched(profile)) return 0;
        var clock = SeasonClock.current(server);
        if (clock.isEmpty()) return 0;
        long today = WorldCalendarSavedData.get(server).worldDayCounter();
        return alignShift(today, clock.get().phase(), clock.get().yearDays());
    }
}
