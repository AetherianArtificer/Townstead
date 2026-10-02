package com.aetherianartificer.townstead.snow;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Optional seasonal integrations implement this; blocks and models never call their APIs. */
@FunctionalInterface
public interface SnowWeatherProvider {
    SnowWeather weather(ServerLevel level, BlockPos pos);
}
