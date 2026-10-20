package com.aetherianartificer.townstead.compat.temperature;

import com.aetherianartificer.townstead.climate.ClimateModel;
import com.aetherianartificer.townstead.temperature.GroundClimate;
import com.aetherianartificer.townstead.temperature.TemperatureSettings;
import com.aetherianartificer.townstead.temperature.ThermalBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Climate without a temperature mod: Townstead's climate model (biome mean, zone seasons, day and
 * night, rain and snow), blended with the ground under cover, then the nearest heat or cooling
 * source. Tunables live in {@code need/temperature.json} and the {@code climate/} data.
 */
public final class BuiltinTemperatureBridge implements AmbientTemperatureBridge {
    public static final BuiltinTemperatureBridge INSTANCE = new BuiltinTemperatureBridge();

    private BuiltinTemperatureBridge() {}

    @Override
    public String id() {
        return "builtin";
    }

    @Override
    public boolean isActive() {
        return true;
    }

    /** Open air without local sources: the climate model, then the ground under cover. */
    public static float climateCelsius(ServerLevel level, BlockPos pos) {
        return GroundClimate.blend(level, pos, ClimateModel.sample(level, pos).air());
    }

    @Override
    public float ambientCelsius(ServerLevel level, BlockPos pos) {
        var room = com.aetherianartificer.townstead.temperature.RoomHeat.at(level, pos);
        if (room.isPresent()) return (float) room.getAsDouble();
        return outdoorCelsius(level, pos);
    }

    /** The open-air reading at a position, ignoring any room around it. */
    public float outdoorCelsius(ServerLevel level, BlockPos pos) {
        TemperatureSettings s = TemperatureSettings.get();
        float celsius = climateCelsius(level, pos);

        float sources = ThermalBlocks.sourceOffset(level, pos, s);
        sources += structureZone(level, pos);
        // Loose blocks and recognised structures add up, but a bonfire ring is not a furnace.
        float warmCap = Math.abs(s.heatSourceOffset()) * 1.5f;
        float coolCap = Math.abs(s.coolingSourceOffset()) * 1.5f;
        celsius += Math.max(-coolCap, Math.min(warmCap, sources));
        return celsius;
    }

    private static float structureZone(ServerLevel level, BlockPos pos) {
        return com.aetherianartificer.townstead.decoration.Decorations.thermalZone(level, pos);
    }
}
