package com.aetherianartificer.townstead.temperature;

import com.aetherianartificer.townstead.hunger.ConsumableTargetClaims;
import com.aetherianartificer.townstead.needs.Amenities;
import com.aetherianartificer.townstead.work.ReachableTargetSelector;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import javax.annotation.Nullable;
import java.util.*;

/** Bounded proposals scored by body recovery, including personal protection and gradual drying. */
public final class ThermalReliefTargets {
    public static final String HEARTH = "hearth", COOL_SPOT = "cool_spot", CLAIM = "thermal_stand";
    private static final ThermalCache<ServerLevel, Long, Float> FELT = new ThermalCache<>(20, 1024);
    private ThermalReliefTargets() {}
    public record Target(BlockPos pos, String description) {}
    private record Spot(Target target, double score) {}
    public static void clearCache() { FELT.clear(); }

    /** A better environment is insufficient: it must reach the recovery band without an unsafe overshoot. */
    public static double recoveryScore(float body, ThermalExposure.Forecast forecast, ThermalProfile profile, double distance) {
        if (!forecast.recovers()) return Double.NEGATIVE_INFINITY;
        if (Math.abs(forecast.target() - profile.neutral()) > profile.band() * 2) return Double.NEGATIVE_INFINITY;
        return 1000 - forecast.recoverySeconds() - distance * 2;
    }
    public static boolean useful(ServerLevel level, VillagerEntityMCA villager, BlockPos pos) {
        if (!level.isLoaded(pos) || !level.getFluidState(pos).isEmpty()) return false;
        var exposure = ThermalExposure.at(level, villager, pos, 0);
        float body = TemperatureData.celsius(TownsteadVillagers.get(villager).needs().bodyTempTenths());
        return Double.isFinite(recoveryScore(body, exposure.forecast(body, 600), exposure.profile(), 0))
                && ThermalExposure.sustainedAt(level, villager, pos, 0).forecast(body, 600).recovers();
    }
    public static @Nullable Target find(ServerLevel level, VillagerEntityMCA villager, boolean cold, ThermalProfile profile) {
        return find(level, villager, cold, profile, Set.of());
    }
    public static @Nullable Target find(ServerLevel level, VillagerEntityMCA villager, boolean cold, ThermalProfile profile,
                                       Set<BlockPos> excluded) {
        BlockPos here = villager.blockPosition();
        if (cold && villager.isInWater()) {
            Set<BlockPos> tried = new HashSet<>();
            for (int i = 0; i < 4; i++) {
                BlockPos exit = ThermalBlocks.nearestStandable(level, here, 16, pos -> !tried.contains(pos));
                if (exit == null) break;
                var path = villager.getNavigation().createPath(exit, 0);
                if (path != null && path.canReach()) return new Target(exit, "dry_land");
                tried.add(exit);
            }
        }
        Search search = new Search(level, villager, excluded);
        search.consider(here, "recover_here", null);
        var home = villager.getResidency().getHome();
        if (home.isPresent() && home.get().dimension().equals(level.dimension())) search.near(home.get().pos(), "home", null);
        BlockPos outside = ThermalBlocks.nearestStandable(level, here, 8, pos -> level.canSeeSky(pos.above()));
        if (outside != null) search.consider(outside, "outside", null);
        BlockPos shelter = ThermalBlocks.nearestStandable(level, here, 12, pos -> !level.canSeeSky(pos.above()));
        if (shelter != null) search.consider(shelter, cold ? "dry_shelter" : "shade", null);
        Amenities.candidates(level, villager).stream()
                .filter(c -> (cold ? HEARTH : COOL_SPOT).equals(c.kind()) || c.definition() != null
                        && (cold ? c.definition().projection().warms() : c.definition().projection().cools()))
                .sorted(Comparator.comparingDouble(c -> c.pos().distSqr(here))).limit(4)
                .forEach(c -> search.approach(c.pos(), c.kind() == null ? "thermal_service" : c.kind(),
                        null));
        for (var pos : com.aetherianartificer.townstead.objectset.ObjectSets.nearestThermal(level, here, 48, cold, 2))
            search.approach(pos, cold ? "hearth_set" : "cool_set", null);
        for (var pos : ThermalBlocks.nearest(level, here, 32, cold, 4))
            search.approach(pos, cold ? "heat_source" : "cooling_source", null);
        var candidates = search.spots.stream().map(s -> new ReachableTargetSelector.Candidate<>(s, s.target().pos())).toList();
        Spot chosen = ReachableTargetSelector.chooseReachable(level, villager, candidates, 0, 6, 200, c -> -c.value().score());
        return chosen == null ? null : chosen.target();
    }
    private static final class Search {
        final ServerLevel level;
        final VillagerEntityMCA villager;
        final BlockPos here;
        final ThermalExposure personal, sustained;
        final float body;
        final List<Spot> spots = new ArrayList<>();
        final Set<String> seen = new HashSet<>();
        final Set<BlockPos> excluded;
        Search(ServerLevel level, VillagerEntityMCA villager, Set<BlockPos> excluded) {
            this.excluded = excluded;
            this.level = level; this.villager = villager; here = villager.blockPosition();
            personal = ThermalExposure.at(level, villager, here, 0);
            sustained = ThermalExposure.sustainedAt(level, villager, here, 0);
            body = TemperatureData.celsius(TownsteadVillagers.get(villager).needs().bodyTempTenths());
        }
        void consider(BlockPos pos, String description, @Nullable Amenities.Candidate service) {
            if (excluded.contains(pos) || !level.isLoaded(pos) || !level.getFluidState(pos).isEmpty()
                    || !seen.add(pos.asLong() + ":" + (service == null ? "" : service.pos().asLong()))
                    || ConsumableTargetClaims.isClaimedByOtherPos(level, villager.getUUID(), CLAIM, pos)) return;
            float ambient = FELT.get(level, pos.asLong(), level.getGameTime(), () -> TemperatureData.ambientCelsius(level, pos));
            var exposure = new ThermalExposure(ThermalConsumables.internalAmbient(villager, ambient), personal.wetness(), false,
                    level.isRainingAt(pos), 0, personal.protection(), personal.profile(), false);
            double distance = Math.sqrt(pos.distSqr(here));
            double score = recoveryScore(body, exposure.forecast(body, 600), personal.profile(), distance);
            var permanent = new ThermalExposure(ambient, personal.wetness(), false,
                    level.isRainingAt(pos), 0, sustained.protection(), personal.profile(), false);
            if (!permanent.forecast(body, 600).recovers()) score = Double.NEGATIVE_INFINITY;
            if (!Double.isFinite(score)) {
                // Slowing harm is useful when recovery is unavailable, but never outranks recovery.
                float improvement = Math.abs(personal.forecast(body, 120).body() - personal.profile().neutral())
                        - Math.abs(exposure.forecast(body, 120).body() - personal.profile().neutral());
                if (improvement <= .05f) return;
                score = -1000 + Math.min(500, improvement * 100) - distance * 2;
                description = "safer_refuge";
            }
            spots.add(new Spot(new Target(pos, description), score));
        }
        void near(BlockPos anchor, String description, @Nullable Amenities.Candidate service) {
            if (!level.isLoaded(anchor)) return;
            BlockPos stand = ThermalBlocks.nearestStandable(level, anchor, 2, pos -> true);
            if (stand != null) consider(stand, description, service);
        }
        void approach(BlockPos anchor, String description, @Nullable Amenities.Candidate service) {
            near(anchor, description, service);
            for (int distance : new int[]{1, 3, 5}) for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos center = anchor.relative(direction, distance);
                BlockPos stand = ThermalBlocks.nearestStandable(level, center, 1,
                        pos -> service == null || pos.distSqr(anchor) <= 9);
                if (stand != null) consider(stand, description, service);
            }
        }
    }
}
