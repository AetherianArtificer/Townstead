package com.aetherianartificer.townstead.compat.snow;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.snow.SnowWeather;
import com.aetherianartificer.townstead.snow.SnowWeatherProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Reflection-only adapter for Serene's 1.20.1 and 1.21.1 seasonal weather hooks. */
public final class SereneSnowWeather implements SnowWeatherProvider {
    private Method precipitation;
    private Method cold;
    private Method dimensionAllowed;
    private Field seasons;
    private Field generateSnow;
    private boolean available;

    public SereneSnowWeather() {
        try {
            Class<?> hooks = Class.forName("sereneseasons.season.SeasonHooks");
            precipitation = hooks.getMethod("getPrecipitationAtSeasonal", Level.class, Holder.class, BlockPos.class);
            cold = hooks.getMethod("coldEnoughToSnowSeasonal", LevelReader.class, BlockPos.class);
            seasons = Class.forName("sereneseasons.init.ModConfig").getField("seasons");
            generateSnow = seasons.getType().getField("generateSnowAndIce");
            dimensionAllowed = seasons.getType().getMethod("isDimensionWhitelisted", ResourceKey.class);
            available = true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            disable(e);
        }
    }

    @Override
    public SnowWeather weather(ServerLevel level, BlockPos pos) {
        if (!available) return SnowWeather.DISABLED;
        try {
            Object config = seasons.get(null);
            if (!generateSnow.getBoolean(config) || !(boolean) dimensionAllowed.invoke(config, level.dimension())) {
                return SnowWeather.DISABLED;
            }
            boolean freezing = (boolean) cold.invoke(null, level, pos);
            boolean falling = freezing && level.isRaining()
                    && precipitation.invoke(null, level, level.getBiome(pos), pos) == Biome.Precipitation.SNOW;
            return new SnowWeather(true, freezing, falling);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            disable(e);
            return SnowWeather.DISABLED;
        }
    }

    private void disable(Throwable error) {
        available = false;
        Townstead.LOGGER.warn("[Snow] Serene snow coating adapter unavailable; coating disabled", error);
    }
}
