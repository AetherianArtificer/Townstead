package com.aetherianartificer.townstead.climate;

import com.aetherianartificer.townstead.calendar.SeasonClock;
import com.aetherianartificer.townstead.compat.temperature.EclipticClimate;
import com.aetherianartificer.townstead.compat.temperature.TfcClimate;
import com.aetherianartificer.townstead.temperature.TemperatureSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import org.jetbrains.annotations.Nullable;

/**
 * Open-air climate at a position: the biome's yearly mean, the zone's seasonal swing peaking a
 * little after the summer solstice, a day/night curve shaped by day length, then rain and snow.
 * Ground, rooms and local heat sources come after this. In a TerraFirmaCraft world TFC's own
 * temperature is the mean and season; its instant reading already swings with the hour, so the
 * day/night curve is off there.
 */
public final class ClimateModel {
    /** Phase of the summer solstice in the season clock's year. */
    static final double SOLSTICE = 0.375;
    static final int VANILLA_DAY = 12000;

    private ClimateModel() {}

    /** Every term of a reading, for diagnostics. */
    public record Sample(float mean, float seasonal, float diurnal, float weather, float air,
                         @Nullable ClimateZone zone, @Nullable SeasonClock.State clock, Snow snow) {}

    /** Falling snow, lying snow, daylight and clear sky at a position. */
    public record Snow(boolean falling, boolean lying, boolean day, boolean clear) {
        public boolean any() { return falling || lying; }
    }

    public static Sample sample(ServerLevel level, BlockPos pos) {
        TemperatureSettings s = TemperatureSettings.get();
        DimensionClimate dimension = DimensionClimate.of(level);
        var biome = level.getBiome(pos);
        ClimateZone zone = dimension.usesBiomes() ? ClimateZones.of(biome).orElse(null) : null;
        var clock = dimension.seasons() ? SeasonClock.current(level.getServer()).orElse(null) : null;

        boolean tfc = dimension.usesBiomes() && TfcClimate.applies(level);
        float mean;
        float seasonal = 0f;
        if (tfc) {
            mean = (float) TfcClimate.average(level, pos).orElse(0);
            seasonal = (float) TfcClimate.instant(level, pos, TfcClimate.now()).orElse(mean) - mean;
        } else if (dimension.usesBiomes()) {
            mean = biomeMean(biome, zone);
            int aboveSea = pos.getY() - level.getSeaLevel();
            if (aboveSea > 0) mean -= aboveSea / s.altitudeBlocksPerDegree();
        } else {
            mean = dimension.fixedCelsius();
        }
        mean += dimension.layerOffset(pos.getY());
        if (clock != null && clock.season() != null) mean += s.seasonOffset(clock.season());
        if (!tfc && clock != null) seasonal = zoneSeasonal(zone, clock, clock.phase());

        boolean sky = level.canSeeSky(pos);
        float diurnal = 0f;
        if (dimension.dayCycle() && !tfc) {
            float range = zone == null ? 0f : zone.diurnalRange();
            if (level.isRaining()) range *= s.cloudDiurnalFactor();
            int dayLength = EclipticClimate.dayLength(level).orElse(VANILLA_DAY);
            diurnal = range * diurnalShape(Math.floorMod(level.getDayTime(), 24000L), dayLength);
            if (level.isNight()) diurnal += s.nightOffset();
            if (!sky) diurnal *= 0.5f;
        }

        float weather = 0f;
        Snow snow = new Snow(false, false, level.isDay(), !level.isRaining());
        if (dimension.weather()) {
            snow = snow(level, pos);
            weather = sky && level.isRainingAt(pos) || snow.falling() ? s.rainOffset() : 0f;
        }
        float air = applySnow(mean + seasonal + diurnal + weather, snow, s.snowfallCelsius(), s.snowDayMax(), s.snowNightCooling())
                + localOffset(level, pos);
        return new Sample(mean, seasonal, diurnal, weather, air, zone, clock, snow);
    }

    /**
     * The day's mean air at a position, {@code daysAhead} days from now: biome mean, layers,
     * altitude and season, without the day/night curve or weather. Cheap enough for crop ticks.
     */
    public static float dailyMean(ServerLevel level, BlockPos pos, int daysAhead) {
        TemperatureSettings s = TemperatureSettings.get();
        DimensionClimate dimension = DimensionClimate.of(level);
        float mean;
        ClimateZone zone = null;
        var biome = level.getBiome(pos);
        var clock = dimension.seasons() ? SeasonClock.current(level.getServer()).orElse(null) : null;
        if (dimension.usesBiomes() && TfcClimate.applies(level)) {
            mean = (float) TfcClimate.dayMean(level, pos, TfcClimate.now() + daysAhead * 24000L)
                    .orElse(TfcClimate.average(level, pos).orElse(0));
            mean += dimension.layerOffset(pos.getY()) + localOffset(level, pos);
            return clock == null || clock.season() == null ? mean : mean + s.seasonOffset(clock.season());
        }
        if (dimension.usesBiomes()) {
            zone = ClimateZones.of(biome).orElse(null);
            mean = biomeMean(biome, zone);
            int aboveSea = pos.getY() - level.getSeaLevel();
            if (aboveSea > 0) mean -= aboveSea / s.altitudeBlocksPerDegree();
        } else {
            mean = dimension.fixedCelsius();
        }
        mean += dimension.layerOffset(pos.getY()) + localOffset(level, pos);
        if (clock == null) return mean;
        if (clock.season() != null) mean += s.seasonOffset(clock.season());
        double phase = clock.phase() + daysAhead / (double) Math.max(1, clock.yearDays());
        return mean + zoneSeasonal(zone, clock, phase);
    }

    /**
     * Only the seasons at a position: the zone's yearly wave plus any legacy per-season offset.
     * Zero where the dimension has no season clock or no season mod runs. For backends that lack
     * seasons of their own.
     */
    public static float seasonalAnomaly(ServerLevel level, BlockPos pos) {
        DimensionClimate dimension = DimensionClimate.of(level);
        if (!dimension.seasons() || !dimension.usesBiomes()) return 0f;
        var clock = SeasonClock.current(level.getServer()).orElse(null);
        if (clock == null) return 0f;
        TemperatureSettings s = TemperatureSettings.get();
        float legacy = clock.season() == null ? 0f : s.seasonOffset(clock.season());
        if (TfcClimate.applies(level)) {
            var day = TfcClimate.dayMean(level, pos, TfcClimate.now());
            var avg = TfcClimate.average(level, pos);
            return day.isPresent() && avg.isPresent() ? legacy + (float) (day.getAsDouble() - avg.getAsDouble()) : legacy;
        }
        return legacy + zoneSeasonal(ClimateZones.of(level.getBiome(pos)).orElse(null), clock, clock.phase());
    }

    /** The biome's yearly mean: a pack-set mean, else its vanilla temperature through the anchors; plus the zone offset. */
    public static float biomeMean(net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome, @Nullable ClimateZone zone) {
        TemperatureSettings s = TemperatureSettings.get();
        var set = s.biomeMean(biome);
        float mean = set.isPresent() ? (float) set.getAsDouble() : s.biomeToCelsius(biome.value().getBaseTemperature());
        return mean + (zone == null ? 0f : zone.meanOffset());
    }

    public static Snow snow(ServerLevel level, BlockPos pos) {
        boolean sky = level.canSeeSky(pos);
        boolean falling = sky && level.isRaining()
                && (EclipticClimate.snowPrecipitation(level, pos)
                || level.getBiome(pos).value().hasPrecipitation() && !level.isRainingAt(pos));
        boolean lying = level.getBlockState(pos).is(BlockTags.SNOW) || level.getBlockState(pos.below()).is(BlockTags.SNOW)
                || sky && EclipticClimate.snowCover(level, pos);
        return new Snow(falling, lying, level.isDay(), !level.isRaining());
    }

    /**
     * Clear-sky open air for a biome at a year phase and time of day, for previews: mean, altitude,
     * layers, season and the day/night curve on a vanilla-length day. No weather, snow or local
     * fields. With no season clock the season terms are zero.
     */
    public static float projected(ServerLevel level, BlockPos pos,
                                  net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome,
                                  @Nullable SeasonClock.State clock, double phase, long dayTicks) {
        TemperatureSettings s = TemperatureSettings.get();
        DimensionClimate dimension = DimensionClimate.of(level);
        ClimateZone zone = dimension.usesBiomes() ? ClimateZones.of(biome).orElse(null) : null;
        float air;
        if (dimension.usesBiomes() && TfcClimate.applies(level)) {
            air = (float) TfcClimate.instant(level, pos, TfcClimate.tickAt(phase, dayTicks))
                    .orElse(TfcClimate.average(level, pos).orElse(0));
            air += dimension.layerOffset(pos.getY());
            if (dimension.seasons() && clock != null) air += s.seasonOffset(seasonAt(phase));
            return air;
        }
        if (dimension.usesBiomes()) {
            air = biomeMean(biome, zone);
            int aboveSea = pos.getY() - level.getSeaLevel();
            if (aboveSea > 0) air -= aboveSea / s.altitudeBlocksPerDegree();
        } else {
            air = dimension.fixedCelsius();
        }
        air += dimension.layerOffset(pos.getY());
        if (dimension.seasons() && clock != null) {
            air += s.seasonOffset(seasonAt(phase)) + zoneSeasonal(zone, clock, phase);
        }
        if (dimension.dayCycle() && zone != null) {
            air += zone.diurnalRange() * diurnalShape(dayTicks, VANILLA_DAY);
            if (dayTicks >= 12000 && dayTicks < 24000) air += s.nightOffset();
        }
        return air;
    }

    /** Pheno local-climate fields (warm groves, cold spots) covering the position. */
    public static float localOffset(ServerLevel level, BlockPos pos) {
        return com.aetherianartificer.townstead.pheno.field.CloudManager.climateOffset(level, pos);
    }

    /** A backend's open-air reading after the same snow rules. */
    public static float applySnow(ServerLevel level, BlockPos pos, float air) {
        TemperatureSettings s = TemperatureSettings.get();
        return applySnow(air, snow(level, pos), s.snowfallCelsius(), s.snowDayMax(), s.snowNightCooling());
    }

    /**
     * Snow is weather, not a temperature. Falling snow caps the air at the snowfall temperature;
     * lying snow holds the day near freezing (melting takes the sun's heat) and chills clear nights.
     */
    static float applySnow(float air, Snow snow, float snowfall, float dayMax, float nightCooling) {
        if (snow.falling()) air = Math.min(air, snowfall);
        if (snow.lying()) {
            if (snow.day()) air = Math.min(air, dayMax);
            else if (snow.clear()) air += nightCooling;
        }
        return air;
    }

    private static com.aetherianartificer.townstead.calendar.Season seasonAt(double phase) {
        return com.aetherianartificer.townstead.calendar.Season.values()[(int) Math.floorMod((long) Math.floor(phase * 4), 4L)];
    }

    /**
     * The zone's season at a year phase: the yearly swing for four-season zones; for wet/dry zones
     * the swing plus the wet or dry offset, or nothing when the season mod reports no wet/dry phase.
     */
    public static float zoneSeasonal(@Nullable ClimateZone zone, SeasonClock.State clock, double phase) {
        if (zone == null) return 0f;
        return switch (zone.seasonKind()) {
            case NONE -> 0f;
            case FOUR_SEASONS -> seasonal(zone.seasonalSwing(), zone.peakLag(), phase);
            case WET_DRY -> WetDry.at(clock, phase)
                    .map(p -> seasonal(zone.seasonalSwing(), zone.peakLag(), phase)
                            + WetDry.offset(zone.dryOffset(), zone.wetOffset(), p.wetness()))
                    .orElse(0f);
        };
    }

    /** The zone's yearly swing, warmest {@code peakLag} of a year after the summer solstice. */
    static float seasonal(float swing, float peakLag, double phase) {
        return (float) (swing * Math.cos(2 * Math.PI * (phase - (SOLSTICE + peakLag))));
    }

    /**
     * Day/night shape from -1 at sunrise to +1 two thirds of the way through daylight (about 14:00
     * on a vanilla day), back to -1 at the next sunrise. Longer days warm for longer.
     */
    static float diurnalShape(long dayTicks, int dayLength) {
        int day = Math.max(2000, Math.min(22000, dayLength));
        double sunrise = 6000 - day / 2.0;
        double x = Math.floorMod((long) Math.floor(dayTicks - sunrise), 24000L);
        double rise = day * 2.0 / 3.0;
        if (x < rise) return (float) -Math.cos(Math.PI * x / rise);
        return (float) Math.cos(Math.PI * (x - rise) / (24000 - rise));
    }
}
