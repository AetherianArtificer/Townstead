package com.aetherianartificer.townstead.compat.temperature;

import com.aetherianartificer.townstead.calendar.CalendarDate;
import com.aetherianartificer.townstead.calendar.Season;
import com.aetherianartificer.townstead.calendar.TownsteadCalendar;
import com.aetherianartificer.townstead.temperature.TemperatureSettings;
import com.aetherianartificer.townstead.temperature.ThermalBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;


/**
 * Climate without a climate mod: the biome's own base temperature mapped through three anchors,
 * then the calendar's season, altitude, rain, night, and the nearest heat or cooling source. Every
 * number lives in {@code data/townstead/need/temperature.json} so packs can retune it.
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

    /** Biome, season, and altitude only: the climate before weather, time of day, walls, and fires. */
    public static float baseCelsius(ServerLevel level, BlockPos pos) {
        TemperatureSettings s = TemperatureSettings.get();
        if (level.dimensionType().ultraWarm()) return s.netherCelsius();
        if (!level.dimensionType().natural()) return s.endCelsius();
        Biome biome = level.getBiome(pos).value();
        float celsius = s.biomeToCelsius(biome.getBaseTemperature());
        celsius += seasonOffset(level, s);
        int aboveSea = pos.getY() - level.getSeaLevel();
        if (aboveSea > 0) celsius -= aboveSea / s.altitudeBlocksPerDegree();
        return celsius;
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
        if (level.dimensionType().ultraWarm()) return s.netherCelsius();
        if (!level.dimensionType().natural()) return s.endCelsius();
        float celsius = baseCelsius(level, pos);

        boolean sky = level.canSeeSky(pos);
        if (sky && level.isRainingAt(pos)) celsius += s.rainOffset();
        if (level.isNight()) celsius += sky ? s.nightOffset() : s.nightOffset() * 0.5f;

        float sources = ThermalBlocks.sourceOffset(level, pos, s);
        sources += structureZone(level, pos);
        // Loose blocks and recognised structures add up, but a bonfire ring is not a furnace.
        float warmCap = Math.abs(s.heatSourceOffset()) * 1.5f;
        float coolCap = Math.abs(s.coolingSourceOffset()) * 1.5f;
        celsius += Math.max(-coolCap, Math.min(warmCap, sources));
        return celsius;
    }

    private static float structureZone(ServerLevel level, BlockPos pos) {
        return com.aetherianartificer.townstead.objectset.ObjectSets.thermalZone(level, pos);
    }

    private static float seasonOffset(ServerLevel level, TemperatureSettings s) {
        CalendarDate today = TownsteadCalendar.today(level.getServer());
        Season season = today == null ? null : today.season();
        if (season == null) return 0f;
        return s.seasonOffset(season);
    }
}
