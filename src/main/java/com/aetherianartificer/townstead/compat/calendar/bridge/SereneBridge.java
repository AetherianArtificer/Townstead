package com.aetherianartificer.townstead.compat.calendar.bridge;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.calendar.Season;
import com.aetherianartificer.townstead.compat.ModCompat;
import com.aetherianartificer.townstead.compat.calendar.CalendarCompat;
import net.minecraft.server.level.ServerLevel;

import java.lang.reflect.Method;
import java.util.Optional;

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
    private static java.util.Map<String, Season> seasonByName;

    private SereneBridge() {}

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
