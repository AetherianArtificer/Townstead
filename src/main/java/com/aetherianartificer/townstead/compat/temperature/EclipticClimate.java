package com.aetherianartificer.townstead.compat.temperature;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.compat.ModCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.lang.reflect.Method;
import java.util.OptionalInt;

/**
 * What Ecliptic Seasons knows about local weather: whether its snow cover lies here (it draws snow
 * on the client, so the server has no snow blocks to find), whether snow is the precipitation
 * here, and how long today's daylight lasts.
 */
public final class EclipticClimate {
    private static boolean initialized;
    private static Method solarTerm, maySnow, snowy, termDayTime;
    private static boolean warned;

    private EclipticClimate() {}

    private static boolean ready(Level level) {
        if (!level.dimensionType().natural() || level.dimensionType().ultraWarm()
                || !ModCompat.isLoaded("eclipticseasons")) return false;
        init();
        return solarTerm != null && snowy != null;
    }

    private static boolean seasonal(Level level) throws ReflectiveOperationException {
        Object term = solarTerm.invoke(null, level);
        return term instanceof Enum<?> season && !season.name().equals("NONE");
    }

    /** Ecliptic's snow cover is down here: its snow season for this biome, or snow weather. */
    public static boolean snowCover(Level level, BlockPos pos) {
        if (!ready(level)) return false;
        try {
            if (!seasonal(level)) return false;
            if (maySnow != null && Boolean.TRUE.equals(maySnow.invoke(null, level, pos))) return true;
            return Boolean.TRUE.equals(snowy.invoke(null, level, pos));
        } catch (ReflectiveOperationException | RuntimeException e) {
            warn(e);
            return false;
        }
    }

    /** Ecliptic says precipitation here falls as snow. */
    public static boolean snowPrecipitation(Level level, BlockPos pos) {
        if (!ready(level)) return false;
        try {
            return seasonal(level) && Boolean.TRUE.equals(snowy.invoke(null, level, pos));
        } catch (ReflectiveOperationException | RuntimeException e) {
            warn(e);
            return false;
        }
    }

    /** Today's daylight in ticks from the current solar term; empty without Ecliptic. */
    public static OptionalInt dayLength(Level level) {
        if (!ready(level) || termDayTime == null) return OptionalInt.empty();
        try {
            Object term = solarTerm.invoke(null, level);
            if (!(term instanceof Enum<?> e) || e.name().equals("NONE")) return OptionalInt.empty();
            return OptionalInt.of(((Number) termDayTime.invoke(term)).intValue());
        } catch (ReflectiveOperationException | RuntimeException e) {
            warn(e);
            return OptionalInt.empty();
        }
    }

    private static synchronized void init() {
        if (initialized) return;
        initialized = true;
        try {
            Class<?> api = Class.forName("com.teamtea.eclipticseasons.api.util.EclipticUtil");
            solarTerm = api.getMethod("getNowSolarTerm", Level.class);
            snowy = api.getMethod("isHereSnowy", Level.class, BlockPos.class);
            try { maySnow = api.getMethod("maySnow", Level.class, BlockPos.class); }
            catch (NoSuchMethodException ignored) {}
            try { termDayTime = solarTerm.getReturnType().getMethod("getDayTime"); }
            catch (NoSuchMethodException ignored) {}
        } catch (ReflectiveOperationException | LinkageError e) {
            warn(e);
        }
    }

    private static void warn(Throwable e) {
        if (warned) return;
        warned = true;
        Townstead.LOGGER.warn("Ecliptic local climate query unavailable; snow and day length fall back to vanilla: {}", e.toString());
    }
}
