package com.aetherianartificer.townstead.snow;

import com.aetherianartificer.townstead.compat.ModCompat;
import com.aetherianartificer.townstead.compat.snow.SereneSnowWeather;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Shared facade. Ecliptic owns its native overlay whenever it is installed. */
public final class SnowCoating {
    private SnowCoating() {}

    public static boolean active() {
        return SnowCoatingPolicy.usesTownsteadCoating(
                ModCompat.isLoaded("sereneseasons"), ModCompat.isLoaded("eclipticseasons"));
    }

    public static SnowWeather weather(ServerLevel level, BlockPos pos) {
        return active() ? Provider.INSTANCE.weather(level, pos) : SnowWeather.DISABLED;
    }

    private static final class Provider {
        private static final SnowWeatherProvider INSTANCE = new SereneSnowWeather();
    }
}
