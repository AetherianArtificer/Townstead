package com.aetherianartificer.townstead.temperature;

import com.aetherianartificer.townstead.climate.ClimateModel;
import com.aetherianartificer.townstead.climate.Evaporation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Humidity and water temperature at a body, from the same climate as the air. */
public final class ThermalAir {
    private ThermalAir() {}

    /** Days the water lags the air: lakes and rivers follow the season late and barely notice the day. */
    static final int WATER_LAG_DAYS = 10;

    /**
     * Relative humidity in air at {@code air} degrees: the outdoor climate's water vapour at this
     * temperature, so heated indoor air is drier and cooled air damper.
     */
    public static float humidity(ServerLevel level, BlockPos pos, float air) {
        var outside = ClimateModel.sample(level, pos);
        return relative(outside.humidity(), outside.air(), air);
    }

    /** The same water vapour as air at {@code fromCelsius} with {@code humidity}, warmed or cooled to {@code toCelsius}. */
    static float relative(float humidity, float fromCelsius, float toCelsius) {
        double vapour = humidity * Evaporation.saturationPressure(fromCelsius);
        return (float) Math.max(0.05, Math.min(1, vapour / Evaporation.saturationPressure(toCelsius)));
    }

    /** Water at a position: the climate some days back, blended toward the ground under cover, never below freezing. */
    public static float water(ServerLevel level, BlockPos pos) {
        float lagged = ClimateModel.dailyMean(level, pos, -WATER_LAG_DAYS);
        return Math.max(0.5f, GroundClimate.blend(level, pos, lagged));
    }
}
