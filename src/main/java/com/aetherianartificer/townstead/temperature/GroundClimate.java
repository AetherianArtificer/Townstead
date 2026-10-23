package com.aetherianartificer.townstead.temperature;

import com.aetherianartificer.townstead.calendar.Season;
import com.aetherianartificer.townstead.calendar.SeasonClock;
import com.aetherianartificer.townstead.compat.temperature.AmbientTemperatureBridge;
import com.aetherianartificer.townstead.compat.temperature.TfcClimate;
import com.aetherianartificer.townstead.climate.BiomeProfile;
import com.aetherianartificer.townstead.climate.ClimateModel;
import com.aetherianartificer.townstead.climate.ClimateZone;
import com.aetherianartificer.townstead.climate.ClimateZones;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.function.Supplier;

/**
 * Temperature under cover of natural terrain. The ground is the surface climate low-pass filtered
 * by depth: each harmonic of the yearly season curve fades as {@code e^(-z sqrt(n) / d)} and
 * arrives {@code z sqrt(n) / d} radians late, the periodic solution of the heat equation. Below
 * that, rock warms with a compressed geothermal gradient and near buried lava.
 */
public final class GroundClimate {
    static final int HARMONICS = 8;
    private static final int MAX_WALK = 48;
    private static final TagKey<Block> TERRAIN = TagKey.create(Registries.BLOCK,
            ResourceLocation.tryParse("townstead:thermal/terrain"));
    private static final ThermalCache<ServerLevel, Long, Probe> PROBES = new ThermalCache<>(200, 8192);

    private GroundClimate() {}

    /** Terrain cover above a position, the rock heat there, and the cave biome's own offset. */
    public record Probe(int depth, float geothermal, float lava, float cave) {}

    public static void clearCache() { PROBES.clear(); }

    public static Probe probe(ServerLevel level, BlockPos pos) {
        return PROBES.get(level, pos.asLong(), level.getGameTime(), () -> {
            int depth = depth(level, pos);
            if (depth <= 0) return new Probe(0, 0f, 0f, 0f);
            TemperatureSettings s = TemperatureSettings.get();
            return new Probe(depth, geothermal(pos.getY(), s.geothermalStartY(), s.geothermalPerBlock(),
                    s.geothermalDeepY(), s.geothermalDeepPerBlock()), lavaHeat(level, pos, s),
                    ClimateZones.profile(level.getBiome(pos)).map(BiomeProfile::undergroundOffset).orElse(0f));
        });
    }

    /** Blocks of natural terrain above head height; zero under open sky, roofs, or where the dimension has no ground model. */
    static int depth(ServerLevel level, BlockPos pos) {
        if (!com.aetherianartificer.townstead.climate.DimensionClimate.of(level).underground() || level.canSeeSky(pos)) return 0;
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
        int head = pos.getY() + 1;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(pos.getX(), top - 1, pos.getZ());
        // Built blocks and air above the terrain are not cover: walk down to the first natural block.
        for (int steps = 0; cursor.getY() > head && steps < MAX_WALK; steps++, cursor.move(0, -1, 0)) {
            if (level.getBlockState(cursor).is(TERRAIN)) return cursor.getY() - head;
        }
        return 0;
    }

    /** How much of the open-air reading still applies at this cover: 1 at the surface, 0 below the layer. */
    public static float surfaceWeight(int depth) {
        return surfaceWeight(depth, TemperatureSettings.get().surfaceLayer());
    }

    static float surfaceWeight(double depth, float layer) {
        if (depth <= 0) return 1f;
        if (layer <= 0) return 0f;
        return (float) Math.max(0, 1 - depth / layer);
    }

    static float geothermal(int y, int startY, float perBlock, int deepY, float deepPerBlock) {
        int deep = Math.min(deepY, startY);
        return Math.max(0, startY - Math.max(y, deep)) * perBlock + Math.max(0, deep - y) * deepPerBlock;
    }

    private static float lavaHeat(ServerLevel level, BlockPos pos, TemperatureSettings s) {
        int radius = s.lavaRockRadius();
        float max = s.lavaRockMax(), heat = s.lavaRockHeat();
        if (radius <= 0 || max <= 0 || heat <= 0) return 0f;
        double limit = (double) radius * radius;
        double[] sum = {0};
        ThermalSourceIndex.forEach(level, pos, radius, radius, () -> sum[0] >= max ? 0 : limit, (p, state) -> {
            if (!state.getFluidState().is(FluidTags.LAVA)) return;
            double distSq = p.distSqr(pos);
            if (distSq <= limit) sum[0] += heat / Math.max(1, Math.sqrt(distSq));
        });
        return (float) Math.min(max, sum[0]);
    }

    /** Built-in climate: blends the open-air reading toward the ground as cover thickens. */
    public static float blend(ServerLevel level, BlockPos pos, float surfaceAir) {
        Probe probe = probe(level, pos);
        float w = surfaceWeight(probe.depth());
        if (w >= 1f) return surfaceAir;
        return w * surfaceAir + (1 - w) * (builtinGround(level, pos, probe) + probe.cave());
    }

    /**
     * What a mod backend's open-air reading lacks: seasons when it runs none, the snow rules near
     * the surface, and rock heat once covered. {@code climate} is the backend's reading without
     * local sources.
     */
    public static float backendShift(ServerLevel level, BlockPos pos, AmbientTemperatureBridge backend,
                                     Supplier<Float> climate) {
        Probe probe = probe(level, pos);
        float w = surfaceWeight(probe.depth());
        float shift = 0f;
        if (w > 0f && !backend.modelsSeasons()) shift += w * ClimateModel.seasonalAnomaly(level, pos);
        if (w > 0f && ClimateModel.snow(level, pos).any()) {
            Float value = climate.get();
            if (value != null && Float.isFinite(value)) {
                float seasoned = value + shift;
                shift += w * (ClimateModel.applySnow(level, pos, seasoned) - seasoned);
            }
        }
        // A backend with its own depth model (Cold Sweat) also has its own cave biome temperatures.
        if (w < 1f) shift += (1 - w) * (probe.lava() + (backend.modelsGeothermal() ? 0f : probe.geothermal() + probe.cave()));
        return shift + ClimateModel.localOffset(level, pos);
    }

    private static float builtinGround(ServerLevel level, BlockPos pos, Probe probe) {
        TemperatureSettings s = TemperatureSettings.get();
        BlockPos surface = new BlockPos(pos.getX(), pos.getY() + 1 + probe.depth(), pos.getZ());
        if (TfcClimate.applies(level)) {
            float mean = (float) TfcClimate.average(level, surface).orElse(0);
            return mean + tfcSeasonal(level, surface, mean, probe.depth(), s) + probe.geothermal() + probe.lava();
        }
        var biome = level.getBiome(surface);
        ClimateZone zone = ClimateZones.of(biome).orElse(null);
        float mean = ClimateModel.biomeMean(biome, zone);
        mean += com.aetherianartificer.townstead.climate.ClimateZones.altitudeOffset(biome, surface.getY(), level.getSeaLevel(), s.altitudeBlocksPerDegree());
        return mean + seasonal(level, zone, probe.depth(), s) + probe.geothermal() + probe.lava();
    }

    /** TFC's season at the surface, damped by depth and read from as far back as the ground lags. */
    private static float tfcSeasonal(ServerLevel level, BlockPos surface, float mean, int depth, TemperatureSettings s) {
        double k = depth / Math.max(0.01, s.groundDampingDepth());
        long lagged = TfcClimate.now() - lagTicks(k, TfcClimate.yearTicks());
        float legacy = SeasonClock.current(level.getServer()).map(c -> (float) legacy(c.phase(), depth, s)).orElse(0f);
        return legacy + (float) (Math.exp(-k) * (TfcClimate.dayMean(level, surface, lagged).orElse(mean) - mean));
    }

    /** A yearly wave arrives {@code k} radians late at depth: that many ticks of the year. */
    static long lagTicks(double k, long yearTicks) {
        return Math.round(k / (2 * Math.PI) * yearTicks);
    }

    /**
     * The surface season, damped and delayed by depth: the zone's yearly wave, plus any legacy
     * per-season offsets a pack still sets. Zero with no season clock.
     */
    private static float seasonal(ServerLevel level, ClimateZone zone, int depth, TemperatureSettings s) {
        var clock = SeasonClock.current(level.getServer());
        if (clock.isEmpty()) return ClimateModel.timeless(zone);
        double year = clock.get().phase();
        double legacy = legacy(year, depth, s);
        if (zone == null || zone.seasonKind() == ClimateZone.SeasonKind.NONE) return (float) legacy;
        if (zone.seasonKind() == ClimateZone.SeasonKind.FOUR_SEASONS)
            return (float) (legacy + zoneWave(zone.seasonalSwing(), zone.peakLag(), year, depth, s.groundDampingDepth()));
        double k = depth / Math.max(0.01, s.groundDampingDepth());
        return (float) (legacy + Math.exp(-k) * ClimateModel.zoneSeasonal(zone, clock.get(), year - k / (2 * Math.PI)));
    }

    private static double legacy(double year, int depth, TemperatureSettings s) {
        float[] quarters = quarterOffsets(s, Season.SPRING, 0);
        return mean(quarters) + anomaly(quarters, year, depth, s.groundDampingDepth());
    }

    /** One yearly cosine at depth: amplitude falls by e^(-z/d) and the peak arrives z/d radians late. */
    static double zoneWave(float swing, float peakLag, double year, double depth, double dampingDepth) {
        double k = depth / Math.max(0.01, dampingDepth);
        return swing * Math.exp(-k) * Math.cos(2 * Math.PI * (year - (0.375 + peakLag)) - k);
    }

    /** Offsets per quarter of the year, anchored so today's quarter holds today's season. */
    static float[] quarterOffsets(TemperatureSettings s, Season today, int todayQuarter) {
        Season[] seasons = Season.values();
        int shift = Math.floorMod(today.ordinal() - todayQuarter, 4);
        float[] quarters = new float[4];
        for (int q = 0; q < 4; q++) quarters[q] = s.seasonOffset(seasons[(q + shift) % 4]);
        return quarters;
    }

    static double mean(float[] quarters) {
        double sum = 0;
        for (float value : quarters) sum += value;
        return sum / quarters.length;
    }

    /** Fourier series of the quarterly step curve, each harmonic damped and delayed by depth. */
    static double anomaly(float[] quarters, double year, double depth, double dampingDepth) {
        double total = 0;
        for (int n = 1; n <= HARMONICS; n++) {
            double w = 2 * Math.PI * n, a = 0, b = 0;
            for (int q = 0; q < 4; q++) {
                double x0 = q / 4.0, x1 = (q + 1) / 4.0;
                a += quarters[q] * (Math.sin(w * x1) - Math.sin(w * x0)) / w;
                b += quarters[q] * (Math.cos(w * x0) - Math.cos(w * x1)) / w;
            }
            double k = depth * Math.sqrt(n) / Math.max(0.01, dampingDepth);
            double phase = w * year - k;
            total += Math.exp(-k) * 2 * (a * Math.cos(phase) + b * Math.sin(phase));
        }
        return total;
    }

    /** One diagnostics line for a position. */
    public static String describe(ServerLevel level, BlockPos pos) {
        Probe probe = probe(level, pos);
        var snow = ClimateModel.snow(level, pos);
        String ground = probe.depth() > 0
                ? String.format(java.util.Locale.ROOT, "%.1f C", builtinGround(level, pos, probe)) : "n/a";
        return String.format(java.util.Locale.ROOT,
                "Ground: cover %d blocks; open-air weight %.2f; builtin ground %s; geothermal +%.1f C; lava +%.1f C; cave %+.1f C; snow %s",
                probe.depth(), surfaceWeight(probe.depth()), ground, probe.geothermal(), probe.lava(), probe.cave(),
                snow.falling() ? "falling" : snow.lying() ? "lying" : "none");
    }
}
