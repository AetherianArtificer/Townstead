package com.aetherianartificer.townstead.temperature;

import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.climate.ClimateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;
import java.util.OptionalDouble;

/**
 * What Townstead tells Bit of a Mess about heated water: the climate around a pool (its water with no heat in it, the
 * room air or open air over it, that air's humidity, and wind out under the sky) and a heater's watts, from the same
 * data as rooms. Only while Townstead's temperature is on; otherwise Bit of a Mess uses its own.
 */
public final class PoolClimate {

    private PoolClimate() {}

    /** The climate around water, in degrees C, humidity 0 to 1 and wind in m/s. */
    public record Air(double water, double air, double humidity, double wind) {}

    /** At the air just over a pool's surface. Reads no pool, so it can't ask itself again. */
    public static Optional<Air> at(ServerLevel level, BlockPos surface) {
        if (!TownsteadConfig.temperatureActive()) return Optional.empty();
        double water = ThermalAir.climateWater(level, surface.below());
        OptionalDouble room = RoomHeat.cachedAirAt(level, surface);
        if (room.isPresent()) {
            float air = (float) room.getAsDouble();
            return Optional.of(new Air(water, air, ThermalAir.humidity(level, surface, air), 0));
        }
        var outside = ClimateModel.sample(level, surface);
        return Optional.of(new Air(water, outside.air(), outside.humidity(), level.canSeeSky(surface) ? 1 : 0));
    }

    /**
     * A heater's watts into water over it, as a room counts a source: appliance data first, then the mod's or the
     * tag's strength. NaN while Townstead's temperature is off.
     */
    public static double watts(ServerLevel level, BlockPos pos, BlockState state) {
        if (!TownsteadConfig.temperatureActive()) return Double.NaN;
        var settings = TemperatureSettings.get();
        float nativeEffect = ThermalBlocks.moddedTemperature(level, pos, state);
        var profile = ThermalBlocks.appliance(state, settings);
        if (profile != null)
            return profile.output(nativeEffect, Float.isFinite(nativeEffect) ? nativeEffect != 0 : ThermalBlocks.isActive(state)).watts();
        float effect = nativeEffect;
        if (!Float.isFinite(effect)) {
            int sign = ThermalBlocks.taggedSource(state);
            effect = sign > 0 ? settings.heatSourceOffset() : sign < 0 ? settings.coolingSourceOffset() : 0;
        }
        return RoomHeatBalance.sourcePower(effect, settings.roomSourcePower(), 1, 1);
    }
}
