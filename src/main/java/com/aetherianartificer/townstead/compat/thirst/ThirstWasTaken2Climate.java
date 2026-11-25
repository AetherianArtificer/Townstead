package com.aetherianartificer.townstead.compat.thirst;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.compat.ModCompat;
import com.aetherianartificer.townstead.compat.temperature.RoomHeatBackend;
import com.aetherianartificer.townstead.temperature.TemperatureData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

import java.lang.reflect.Field;
import java.util.function.ToDoubleFunction;

/**
 * ThirstWasTaken2 drains a player by the temperature it can measure. It measures only through its
 * own Cold Sweat link, which Townstead already reaches through Cold Sweat's world query. Without
 * Cold Sweat, the same environment the temperature mod receives (rooms, heat sources, Townstead's
 * climate) goes into that one slot instead, so the drain agrees with what the player feels hot in.
 * The slot is filled only when it is empty, and only answers while Townstead temperature runs.
 */
public final class ThirstWasTaken2Climate {
    /** Cold Sweat world units and biome base temperature share a scale of 25 Celsius per unit. */
    private static final float CELSIUS_PER_UNIT = 25f;
    private static boolean installed;

    private ThirstWasTaken2Climate() {}

    public static synchronized void install() {
        if (installed || !ModCompat.isLoaded(ThirstWasTaken2Bridge.MOD_ID) || ModCompat.isLoaded("cold_sweat")) return;
        try {
            Class<?> manager = Class.forName("com.thirstwastaken2.data.ThirstManager");
            Field slot = manager.getDeclaredField("climateTemperature");
            slot.setAccessible(true);
            if (slot.get(null) != null) return;
            ToDoubleFunction<Player> source = ThirstWasTaken2Climate::measure;
            manager.getMethod("setClimateTemperature", ToDoubleFunction.class).invoke(null, source);
            installed = true;
            Townstead.LOGGER.info("ThirstWasTaken2 player climate follows Townstead's temperature.");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            Townstead.LOGGER.warn("Unable to feed Townstead's climate to ThirstWasTaken2.", e);
        }
    }

    public static double measure(Player player) {
        if (player == null || !(player.level() instanceof ServerLevel level) || !TownsteadConfig.temperatureActive()) {
            return Double.NaN;
        }
        var environment = RoomHeatBackend.environment(level, player.blockPosition());
        float celsius = environment.isPresent() ? (float) environment.getAsDouble()
                : TemperatureData.ambientCelsius(level, player.blockPosition());
        return Float.isFinite(celsius) ? celsius / CELSIUS_PER_UNIT : Double.NaN;
    }
}
