package com.aetherianartificer.townstead.compat.temperature;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Adds only the climate discrepancy to LSO's weather contribution. */
public final class LsoClimate {
    private static List<Supplier<?>> climateModifiers;
    private static Method influence;
    private static boolean initialized, warned;

    private LsoClimate() {}

    public static float weather(Player player, Level level, BlockPos pos, float original) {
        float ceiling = EclipticClimate.ceiling(level, pos);
        if (!Float.isFinite(ceiling)) return original;
        init();
        if (climateModifiers == null) return original;
        try {
            float climate = original;
            for (Supplier<?> holder : climateModifiers) {
                climate += ((Number) influence.invoke(holder.get(), player, level, pos)).floatValue();
            }
            return correctedWeather(original, climate, ceiling);
        } catch (ReflectiveOperationException | RuntimeException e) {
            warn(e);
            return original;
        }
    }

    static float correctedWeather(float weather, float climate, float ceiling) {
        if (!Float.isFinite(climate) || !Float.isFinite(ceiling) || climate <= ceiling) return weather;
        return weather + (ceiling - climate);
    }

    private static synchronized void init() {
        if (initialized) return;
        initialized = true;
        try {
            Class<?> registry = Class.forName("sfiomn.legendarysurvivaloverhaul.registry.TemperatureModifierRegistry");
            Class<?> modifier = Class.forName("sfiomn.legendarysurvivaloverhaul.api.temperature.ModifierBase");
            influence = modifier.getMethod("getWorldInfluence", Player.class, Level.class, BlockPos.class);
            List<Supplier<?>> holders = new ArrayList<>();
            // WEATHER is the caller: never sample it recursively. Local blocks, clothing,
            // wetness, entities and dynamic resistances remain entirely LSO's responsibility.
            for (String name : List.of("BIOME", "ALTITUDE", "DIMENSION", "TIME", "SERENE_SEASONS", "ECLIPTIC_SEASONS")) {
                holders.add((Supplier<?>) registry.getField(name).get(null));
            }
            climateModifiers = List.copyOf(holders);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            warn(e);
        }
    }

    private static void warn(Throwable e) {
        if (warned) return;
        warned = true;
        Townstead.LOGGER.warn("LSO climate reconciliation unavailable; retaining LSO weather: {}", e.toString());
    }
}
