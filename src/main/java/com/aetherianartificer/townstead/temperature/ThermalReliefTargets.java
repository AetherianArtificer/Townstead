package com.aetherianartificer.townstead.temperature;

import com.aetherianartificer.townstead.needs.Amenities;
import com.aetherianartificer.townstead.work.ReachableTargetSelector;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.pathfinder.Path;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Where a cold or hot villager goes. Cold: the village's {@code hearth} amenities, then any sheltered
 * heat source nearby, and for an ectotherm any sunlit spot to bask in. Hot: {@code cool_spot}
 * amenities, then any cooling source, then plain shade.
 */
public final class ThermalReliefTargets {
    public static final String HEARTH = "hearth";
    public static final String COOL_SPOT = "cool_spot";

    private static final int SOURCE_RADIUS = 16;
    private static final int SHADE_RADIUS = 12;
    private static final int BASK_RADIUS = 8;
    private static final int CLOSE_ENOUGH = 0;
    private static final int MAX_PATH_ATTEMPTS = 4;
    private static final int UNREACHABLE_TTL_TICKS = 200;
    private static final int SOURCE_LIMIT = 6;
    private static final int SET_RADIUS = 48;
    private static final float HOME_GAIN_CELSIUS = 3f;

    private ThermalReliefTargets() {}

    public record Target(BlockPos pos, String description) {}

    public static @Nullable Target find(ServerLevel level, VillagerEntityMCA villager, boolean cold, boolean ectotherm) {
        BlockPos here = villager.blockPosition();
        String kind = cold ? HEARTH : COOL_SPOT;

        if (cold && villager.isInWater()) {
            BlockPos exit = nearestReachable(level, villager, here, SOURCE_RADIUS, pos -> true);
            if (exit != null) return new Target(exit, "dry_land");
        }
        Target home = homeRoom(level, villager, cold);
        if (home != null) return home;

        List<ReachableTargetSelector.Candidate<BlockPos>> amenities = new ArrayList<>();
        for (Amenities.Candidate candidate : Amenities.candidates(level, villager)) {
            if (!kind.equals(candidate.kind())) continue;
            // Stateful amenities declare their own availability (including machines with no heat-source tag).
            if (cold && (candidate.definition() == null || candidate.definition().requires() == null)
                    && !ThermalBlocks.isHeatSource(level, candidate.pos(), level.getBlockState(candidate.pos()))) continue;
            if (candidate.definition() != null && candidate.definition().requires() != null) {
                float current = TemperatureData.ambientCelsius(level, here);
                BlockPos relief = ThermalBlocks.nearestStandable(level, candidate.pos(), 2, pos -> {
                    float at = TemperatureData.ambientCelsius(level, pos);
                    return cold ? at > current : at < current;
                });
                if (relief != null) amenities.add(new ReachableTargetSelector.Candidate<>(relief, relief));
            } else {
                BlockPos stand = ThermalBlocks.nearestStandable(level, candidate.pos(), 2, pos -> true);
                if (stand != null) amenities.add(new ReachableTargetSelector.Candidate<>(stand, stand));
            }
        }
        BlockPos chosen = chooseStandPositions(level, villager, amenities);
        if (chosen != null) return new Target(chosen, kind);

        List<ReachableTargetSelector.Candidate<BlockPos>> sets = new ArrayList<>();
        for (BlockPos pos : com.aetherianartificer.townstead.objectset.ObjectSets.nearestThermal(level, here, SET_RADIUS, cold, SOURCE_LIMIT)) {
            sets.add(new ReachableTargetSelector.Candidate<>(pos, pos));
        }
        chosen = choose(level, villager, sets);
        if (chosen != null) return new Target(chosen, cold ? "hearth_set" : "cool_set");

        List<ReachableTargetSelector.Candidate<BlockPos>> sources = new ArrayList<>();
        for (BlockPos pos : ThermalBlocks.nearest(level, here, SOURCE_RADIUS, cold, cold, SOURCE_LIMIT)) {
            sources.add(new ReachableTargetSelector.Candidate<>(pos, pos));
        }
        chosen = choose(level, villager, sources);
        if (chosen != null) return new Target(chosen, cold ? "heat_source" : "cooling_source");

        if (cold && ectotherm && level.isDay() && !level.isRaining()) {
            BlockPos bask = nearestReachable(level, villager, here, BASK_RADIUS,
                    pos -> level.canSeeSky(pos) && level.getBrightness(LightLayer.SKY, pos) >= 15);
            if (bask != null) return new Target(bask, "bask");
        }
        if (!cold) {
            BlockPos shade = nearestReachable(level, villager, here, SHADE_RADIUS,
                    pos -> !level.canSeeSky(pos) && level.getBrightness(LightLayer.BLOCK, pos) < 12);
            if (shade != null) return new Target(shade, "shade");
        }
        if (cold && com.aetherianartificer.townstead.villager.TownsteadVillagers.get(villager).needs().wet()) {
            BlockPos shelter = nearestReachable(level, villager, here, SHADE_RADIUS,
                    pos -> !level.canSeeSky(pos));
            if (shelter != null) return new Target(shelter, "dry_shelter");
        }
        return null;
    }

    private static @Nullable BlockPos nearestReachable(ServerLevel level, VillagerEntityMCA villager,
            BlockPos center, int radius, java.util.function.Predicate<BlockPos> test) {
        java.util.Set<BlockPos> tried = new java.util.HashSet<>();
        for (int attempt = 0; attempt < MAX_PATH_ATTEMPTS; attempt++) {
            BlockPos pos = ThermalBlocks.nearestStandable(level, center, radius,
                    candidate -> !tried.contains(candidate) && test.test(candidate));
            if (pos == null) return null;
            Path path = villager.getNavigation().createPath(pos, 0);
            if (path != null && path.canReach()) return pos;
            tried.add(pos);
        }
        return null;
    }

    /**
     * The villager's own home, when its bed sits in an enclosed room whose temperature beats the
     * villager's current ambient by a clear margin in the right direction. The active backend supplies the home reading as well as the current reading.
     */
    private static @Nullable Target homeRoom(ServerLevel level, VillagerEntityMCA villager, boolean cold) {
        java.util.Optional<net.minecraft.core.GlobalPos> bed = villager.getResidency().getHome();
        if (bed.isEmpty() || !bed.get().dimension().equals(level.dimension())) return null;
        if (!level.isLoaded(bed.get().pos())) return null;
        float homeAmbient = TemperatureData.ambientCelsius(level, bed.get().pos());
        float here = TemperatureData.celsius(com.aetherianartificer.townstead.villager.TownsteadVillagers.get(villager).needs().ambientTenths());
        float gain = cold ? homeAmbient - here : here - homeAmbient;
        if (gain < HOME_GAIN_CELSIUS) return null;
        BlockPos target = nearestReachable(level, villager, bed.get().pos(), 2, pos -> true);
        if (target == null) return null;
        return new Target(target, "home");
    }

    private static @Nullable BlockPos choose(ServerLevel level, VillagerEntityMCA villager,
                                             List<ReachableTargetSelector.Candidate<BlockPos>> candidates) {
        if (candidates.isEmpty()) return null;
        List<ReachableTargetSelector.Candidate<BlockPos>> dry = new ArrayList<>();
        for (var candidate : candidates) {
            BlockPos stand = ThermalBlocks.nearestStandable(level, candidate.pos(), 2, pos -> true);
            if (stand != null) dry.add(new ReachableTargetSelector.Candidate<>(stand, stand));
        }
        return chooseStandPositions(level, villager, dry);
    }

    private static @Nullable BlockPos chooseStandPositions(ServerLevel level, VillagerEntityMCA villager,
            List<ReachableTargetSelector.Candidate<BlockPos>> candidates) {
        if (candidates.isEmpty()) return null;
        return ReachableTargetSelector.chooseReachable(level, villager, candidates, CLOSE_ENOUGH, MAX_PATH_ATTEMPTS,
                UNREACHABLE_TTL_TICKS, candidate -> villager.distanceToSqr(candidate.pos().getX() + 0.5,
                        candidate.pos().getY() + 0.5, candidate.pos().getZ() + 0.5));
    }
}
