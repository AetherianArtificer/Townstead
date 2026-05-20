package com.aetherianartificer.townstead.compat.calendar.bridge;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.calendar.Season;
import com.aetherianartificer.townstead.compat.ModCompat;
import com.aetherianartificer.townstead.compat.calendar.CalendarCompat;
import net.minecraft.server.level.ServerLevel;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Reflection-only bridge to Ecliptic Seasons' public API
 * ({@code com.teamtea.eclipticseasons.api}). The API is stable across the
 * Forge 1.20.1 and NeoForge 1.21.x branches; 1.21.x adds methods on top
 * (we don't depend on those).
 *
 * Strategy: read the current SolarTerm via
 * {@code EclipticUtil.getNowSolarTerm(Level)}, then ask the term what season
 * it belongs to. Falls back to {@code EclipticUtil.INSTANCE.getSeason(level)}
 * if the term-to-season accessor isn't present.
 */
public final class EclipticBridge {

    private static volatile boolean probeAttempted = false;
    private static volatile boolean probeOk = false;
    private static Method getNowSolarTerm;
    private static Method solarTermGetSeason;
    private static Object eclipticUtilInstance;
    private static Method instanceGetSeason;
    private static Map<String, Season> seasonByName;

    private EclipticBridge() {}

    public static Optional<Season> currentSeason(ServerLevel level) {
        if (level == null) return Optional.empty();
        if (!ModCompat.isLoaded(CalendarCompat.ECLIPTIC_MOD_ID)) return Optional.empty();
        if (!ensureProbe()) return Optional.empty();
        try {
            if (getNowSolarTerm != null && solarTermGetSeason != null) {
                Object term = getNowSolarTerm.invoke(null, level);
                if (term != null) {
                    Object season = solarTermGetSeason.invoke(term);
                    if (season instanceof Enum<?> e) {
                        return Optional.ofNullable(seasonByName.get(e.name()));
                    }
                }
            }
            if (instanceGetSeason != null && eclipticUtilInstance != null) {
                Object season = instanceGetSeason.invoke(eclipticUtilInstance, level);
                if (season instanceof Enum<?> e) {
                    return Optional.ofNullable(seasonByName.get(e.name()));
                }
            }
        } catch (Throwable ignored) {
            // fall through
        }
        return Optional.empty();
    }

    private static synchronized boolean ensureProbe() {
        if (probeAttempted) return probeOk;
        probeAttempted = true;
        try {
            Class<?> util = Class.forName("com.teamtea.eclipticseasons.api.util.EclipticUtil");
            Class<?> levelCls = Class.forName("net.minecraft.world.level.Level");
            try {
                getNowSolarTerm = util.getMethod("getNowSolarTerm", levelCls);
            } catch (NoSuchMethodException ignored) {}

            try {
                Class<?> solarTermCls = Class.forName("com.teamtea.eclipticseasons.api.constant.solar.SolarTerm");
                try {
                    solarTermGetSeason = solarTermCls.getMethod("getSeason");
                } catch (NoSuchMethodException ignored) {}
            } catch (Throwable ignored) {}

            try {
                java.lang.reflect.Field inst = util.getField("INSTANCE");
                eclipticUtilInstance = inst.get(null);
            } catch (Throwable ignored) {}
            if (eclipticUtilInstance != null) {
                try {
                    instanceGetSeason = util.getMethod("getSeason", levelCls);
                } catch (NoSuchMethodException ignored) {}
            }

            seasonByName = new HashMap<>();
            seasonByName.put("SPRING", Season.SPRING);
            seasonByName.put("SUMMER", Season.SUMMER);
            seasonByName.put("AUTUMN", Season.AUTUMN);
            seasonByName.put("WINTER", Season.WINTER);

            probeOk = getNowSolarTerm != null || instanceGetSeason != null;
        } catch (Throwable t) {
            Townstead.LOGGER.info("[Calendar] Ecliptic Seasons bridge unavailable: {}", t.getMessage());
            probeOk = false;
        }
        return probeOk;
    }
}
