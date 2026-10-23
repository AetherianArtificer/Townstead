package com.aetherianartificer.townstead.compat.calendar.bridge;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.calendar.Season;
import com.aetherianartificer.townstead.compat.ModCompat;
import com.aetherianartificer.townstead.compat.calendar.CalendarCompat;
import net.minecraft.server.level.ServerLevel;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Reflection-only bridge to Serene Seasons' public API. Identical across
 * 1.20.1 Forge and 1.21.1 NeoForge (Serene's API has not drifted), so no
 * stonecutter conditionals required.
 *
 * Probes once on first call, caches MethodHandles. On any reflection failure
 * the bridge degrades silently to {@link Optional#empty()} and the calendar
 * falls back to its structural math, per
 * [[feedback_seasons_only_from_mods]]: we never invent a season when the
 * authority isn't available.
 *
 * Reads: {@code sereneseasons.api.season.SeasonHelper.getSeasonState(Level)}
 * then {@code ISeasonState.getSubSeason()}, then the SubSeason's parent
 * {@code Season} via its {@code getSeason()} accessor.
 */
public final class SereneBridge {

    private static volatile boolean probeAttempted = false;
    private static volatile boolean probeOk = false;
    private static Method seasonHelperGetState;
    private static Method iSeasonStateGetSubSeason;
    private static Method subSeasonGetSeason;
    private static Method iSeasonStateGetDay;
    private static Method iSeasonStateGetDayDuration;
    private static Method iSeasonStateGetSubSeasonDuration;
    private static Method iSeasonStateGetCycleDuration;
    private static Method iSeasonStateGetSeasonCycleTicks;
    private static Field seasonTimeZero;
    private static java.util.Map<String, Season> seasonByName;

    private SereneBridge() {}

    /**
     * Full snapshot of Serene's live cycle state for the given level. All
     * derived from {@code ISeasonState} at query time, per
     * [[feedback_seasons_only_from_mods]].
     *
     * <p>{@code dayOfYear} is 0-based within the current cycle. {@code
     * subSeasonDays} is the configured length of each of the 12 sub-seasons
     * (Serene's {@code sub_season_duration}); {@code daysPerCycle} is always
     * {@code 12 * subSeasonDays}. {@code season} is nullable if the sub-season
     * can't be mapped.</p>
     */
    public record SereneState(int dayOfYear, int subSeasonDays, int daysPerCycle, Season season) {}

    public static Optional<Season> currentSeason(ServerLevel level) {
        if (level == null) return Optional.empty();
        if (!ModCompat.isLoaded(CalendarCompat.SERENE_MOD_ID)) return Optional.empty();
        if (!ensureProbe()) return Optional.empty();
        try {
            Object state = seasonHelperGetState.invoke(null, level);
            if (state == null) return Optional.empty();
            Object subSeason = iSeasonStateGetSubSeason.invoke(state);
            if (subSeason == null) return Optional.empty();
            Object season = subSeasonGetSeason.invoke(subSeason);
            if (season == null) return Optional.empty();
            Season mapped = seasonByName.get(((Enum<?>) season).name());
            return Optional.ofNullable(mapped);
        } catch (Throwable t) {
            return Optional.empty();
        }
    }

    /**
     * Live cycle state (position + configured layout) for {@code level}, or
     * empty when Serene isn't present or the API can't be read. Used by
     * {@code SereneMath} so the displayed date tracks Serene's own clock.
     */
    public static Optional<SereneState> currentState(ServerLevel level) {
        if (level == null) return Optional.empty();
        if (!ModCompat.isLoaded(CalendarCompat.SERENE_MOD_ID)) return Optional.empty();
        if (!ensureProbe()) return Optional.empty();
        try {
            Object state = seasonHelperGetState.invoke(null, level);
            if (state == null) return Optional.empty();
            int dayDuration = ((Number) iSeasonStateGetDayDuration.invoke(state)).intValue();
            if (dayDuration <= 0) return Optional.empty();
            int subSeasonTicks = ((Number) iSeasonStateGetSubSeasonDuration.invoke(state)).intValue();
            int cycleTicks = ((Number) iSeasonStateGetCycleDuration.invoke(state)).intValue();
            int subSeasonDays = subSeasonTicks / dayDuration;
            int daysPerCycle = cycleTicks / dayDuration;
            if (subSeasonDays <= 0 || daysPerCycle <= 0) return Optional.empty();
            int rawDay = ((Number) iSeasonStateGetDay.invoke(state)).intValue();
            int dayOfYear = Math.floorMod(rawDay, daysPerCycle);

            Season season = null;
            Object subSeason = iSeasonStateGetSubSeason.invoke(state);
            if (subSeason != null) {
                Object seasonObj = subSeasonGetSeason.invoke(subSeason);
                if (seasonObj != null) season = seasonByName.get(((Enum<?>) seasonObj).name());
            }
            return Optional.of(new SereneState(dayOfYear, subSeasonDays, daysPerCycle, season));
        } catch (Throwable t) {
            return Optional.empty();
        }
    }

    /** Serene's position in its cycle, 0 at the start of early spring, from its own tick count. */
    public static java.util.OptionalDouble phase(ServerLevel level) {
        if (level == null || !ModCompat.isLoaded(CalendarCompat.SERENE_MOD_ID) || !ensureProbe()
                || iSeasonStateGetSeasonCycleTicks == null) return java.util.OptionalDouble.empty();
        try {
            Object state = seasonHelperGetState.invoke(null, level);
            if (state == null) return java.util.OptionalDouble.empty();
            int cycle = ((Number) iSeasonStateGetCycleDuration.invoke(state)).intValue();
            int ticks = ((Number) iSeasonStateGetSeasonCycleTicks.invoke(state)).intValue();
            if (cycle <= 0) return java.util.OptionalDouble.empty();
            return java.util.OptionalDouble.of(Math.floorMod(ticks, cycle) / (double) cycle);
        } catch (Throwable t) {
            return java.util.OptionalDouble.empty();
        }
    }

    /**
     * Serene's configured sub-season length in days ({@code sub_season_duration}),
     * read from {@code SeasonTime.ZERO} so no level is required. Empty when
     * Serene isn't present. Used by {@code SereneProfileSource} to size the 12
     * sub-season "months" to whatever the player configured.
     */
    public static OptionalInt configSubSeasonDays() {
        if (!ModCompat.isLoaded(CalendarCompat.SERENE_MOD_ID)) return OptionalInt.empty();
        if (!ensureProbe()) return OptionalInt.empty();
        try {
            Object zero = seasonTimeZero.get(null);
            if (zero == null) return OptionalInt.empty();
            int dayDuration = ((Number) iSeasonStateGetDayDuration.invoke(zero)).intValue();
            if (dayDuration <= 0) return OptionalInt.empty();
            int subSeasonTicks = ((Number) iSeasonStateGetSubSeasonDuration.invoke(zero)).intValue();
            int subSeasonDays = subSeasonTicks / dayDuration;
            return subSeasonDays > 0 ? OptionalInt.of(subSeasonDays) : OptionalInt.empty();
        } catch (Throwable t) {
            return OptionalInt.empty();
        }
    }

    private static synchronized boolean ensureProbe() {
        if (probeAttempted) return probeOk;
        probeAttempted = true;
        try {
            Class<?> helper = Class.forName("sereneseasons.api.season.SeasonHelper");
            Class<?> levelCls = Class.forName("net.minecraft.world.level.Level");
            seasonHelperGetState = helper.getMethod("getSeasonState", levelCls);

            Class<?> iSeasonState = Class.forName("sereneseasons.api.season.ISeasonState");
            iSeasonStateGetSubSeason = iSeasonState.getMethod("getSubSeason");

            Class<?> subSeasonCls = Class.forName("sereneseasons.api.season.Season$SubSeason");
            subSeasonGetSeason = subSeasonCls.getMethod("getSeason");

            iSeasonStateGetDay = iSeasonState.getMethod("getDay");
            iSeasonStateGetDayDuration = iSeasonState.getMethod("getDayDuration");
            iSeasonStateGetSubSeasonDuration = iSeasonState.getMethod("getSubSeasonDuration");
            iSeasonStateGetCycleDuration = iSeasonState.getMethod("getCycleDuration");
            try { iSeasonStateGetSeasonCycleTicks = iSeasonState.getMethod("getSeasonCycleTicks"); }
            catch (NoSuchMethodException ignored) {}

            Class<?> seasonTimeCls = Class.forName("sereneseasons.season.SeasonTime");
            seasonTimeZero = seasonTimeCls.getField("ZERO");

            seasonByName = new java.util.HashMap<>();
            seasonByName.put("SPRING", Season.SPRING);
            seasonByName.put("SUMMER", Season.SUMMER);
            seasonByName.put("AUTUMN", Season.AUTUMN);
            seasonByName.put("WINTER", Season.WINTER);

            probeOk = true;
        } catch (Throwable t) {
            Townstead.LOGGER.info("[Calendar] Serene Seasons bridge unavailable: {}", t.getMessage());
            probeOk = false;
        }
        return probeOk;
    }
}
