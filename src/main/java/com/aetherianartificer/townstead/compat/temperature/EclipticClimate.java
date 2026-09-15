package com.aetherianartificer.townstead.compat.temperature;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.compat.ModCompat;
import com.aetherianartificer.townstead.temperature.TemperatureSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

import java.lang.reflect.Method;

/** Reconciles environmental Celsius with Ecliptic's local snow climate, before local heat. */
public final class EclipticClimate {
    private static boolean initialized;
    private static Method solarTerm, maySnow, snowy, temperature;
    private static boolean warned;

    private EclipticClimate() {}

    /** NaN means no constraint. Old APIs can still report actual snowfall. */
    public static float ceiling(Level level, BlockPos pos) {
        if (!level.dimensionType().natural() || level.dimensionType().ultraWarm()
                || !ModCompat.isLoaded("eclipticseasons")) return Float.NaN;
        init();
        if (solarTerm == null || snowy == null) return Float.NaN;
        try {
            Object term = solarTerm.invoke(null, level);
            if (!(term instanceof Enum<?> season) || season.name().equals("NONE")) return Float.NaN;
            boolean snowClimate = maySnow != null && Boolean.TRUE.equals(maySnow.invoke(null, level, pos));
            // Weather effects can override the normal snow season.
            snowClimate |= Boolean.TRUE.equals(snowy.invoke(null, level, pos));
            if (!snowClimate) return Float.NaN;
            float climate = Float.NaN;
            if (temperature != null) {
                Object raw = temperature.invoke(null, level, level.getBiome(pos).value(), pos);
                if (raw instanceof Number n && Float.isFinite(n.floatValue())) {
                    climate = TemperatureSettings.get().biomeToCelsius(n.floatValue());
                }
            }
            return snowCeiling(climate);
        } catch (ReflectiveOperationException | RuntimeException e) {
            warn(e);
            return Float.NaN;
        }
    }

    static float snowCeiling(float climateCelsius) {
        // Snow periods are authored independently of Ecliptic's biome-temperature curve.
        return Float.isFinite(climateCelsius) ? Math.min(0f, climateCelsius) : 0f;
    }

    public static float reconcile(float climate, float ceiling) {
        return Float.isFinite(climate) && Float.isFinite(ceiling) ? Math.min(climate, ceiling) : climate;
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
            try { temperature = api.getMethod("getTemperatureFloat", Level.class, Biome.class, BlockPos.class); }
            catch (NoSuchMethodException ignored) {}
        } catch (ReflectiveOperationException | LinkageError e) {
            warn(e);
        }
    }

    private static void warn(Throwable e) {
        if (warned) return;
        warned = true;
        Townstead.LOGGER.warn("Ecliptic local climate query unavailable; retaining the temperature backend reading: {}", e.toString());
    }
}
