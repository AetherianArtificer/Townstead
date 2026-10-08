package com.aetherianartificer.townstead.work.site;

import com.aetherianartificer.townstead.pheno.reservation.ReservationSpec;
import com.aetherianartificer.townstead.work.station.WorkstationV2Def;
import com.aetherianartificer.townstead.work.station.Workstations;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Entity-powered workstation discovery and per-worksite assignment. */
public final class WorksiteDrivers {

    private static final long CANDIDATE_CACHE_TICKS = 20L;
    private record CacheKey(int server, ResourceLocation dimension, long worksite, String station) {}
    private record CacheEntry(long gameTime, List<Candidate> candidates) {}
    private static final java.util.Map<CacheKey, CacheEntry> CANDIDATE_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** A loaded animal which the player can recognise and assign. */
    public record Candidate(UUID uuid, ResourceLocation type, String name, BlockPos position) {}

    private WorksiteDrivers() {}

    private record EngagementAt(BlockPos station, ReservationSpec reservation) {}

    /** Entity engagements required by stations physically belonging to this worksite. */
    private static List<EngagementAt> engagements(ServerLevel level, Worksite site) {
        return engagements(level, site, null);
    }

    /** Engagements belonging to one catalogue station, or all of them when unfiltered. */
    private static List<EngagementAt> engagements(ServerLevel level, Worksite site,
                                                   @Nullable ResourceLocation stationBlock) {
        if (level == null || site == null) return List.of();
        WorkstationV2Def wanted = stationBlock == null
                ? null : Workstations.v2ByBlockId(stationBlock);
        if (stationBlock != null && wanted == null) return List.of();
        List<EngagementAt> out = new ArrayList<>();
        for (long packed : Worksites.extentOf(level, site)) {
            BlockPos station = BlockPos.of(packed);
            WorkstationV2Def def = Workstations.v2ByState(level.getBlockState(station));
            if (def != null && def.reservation() != null
                    && (wanted == null || wanted.id().equals(def.id()))) {
                out.add(new EngagementAt(station, def.reservation()));
            }
        }
        return List.copyOf(out);
    }

    public static boolean supportsDrivers(ServerLevel level, Worksite site) {
        return !engagements(level, site).isEmpty();
    }

    /** Whether this place's authored selector can fall back to its active worker. */
    public static boolean supportsWorkerFallback(ServerLevel level, Worksite site) {
        return engagements(level, site).stream()
                .anyMatch(at -> at.reservation().referencesOrigin());
    }

    /** Whether a particular station allows its active worker to operate it. */
    public static boolean supportsWorkerFallback(ServerLevel level, Worksite site,
                                                  @Nullable ResourceLocation stationBlock) {
        return engagements(level, site, stationBlock).stream()
                .anyMatch(at -> at.reservation().referencesOrigin());
    }

    /** Whether the current explicit/automatic policy resolves to a usable loaded animal. */
    public static boolean assignmentAvailable(ServerLevel level, Worksite site) {
        List<EngagementAt> engagements = engagements(level, site);
        if (engagements.isEmpty()) return true;
        if (site.driver() == null) {
            return supportsWorkerFallback(level, site) || !candidates(level, site).isEmpty();
        }
        net.minecraft.world.entity.Entity entity = level.getEntity(site.driver());
        if (!(entity instanceof Mob mob) || !available(mob)) return false;
        for (EngagementAt at : engagements) {
            if (at.reservation().accepts(level, at.station(), site.villageId(), mob)) return true;
        }
        return false;
    }

    /**
     * Loaded candidates inside the village border.  This runs only for an open Order screen or
     * when an unassigned machine begins a shift; it is not a per-tick village scan.
     */
    public static List<Candidate> candidates(ServerLevel level, Worksite site) {
        return candidates(level, site, null);
    }

    /** Loaded candidates accepted by the station which produces one specific order. */
    public static List<Candidate> candidates(ServerLevel level, Worksite site,
                                             @Nullable ResourceLocation stationBlock) {
        CacheKey cacheKey = new CacheKey(System.identityHashCode(level.getServer()),
                level.dimension().location(), site.id(),
                stationBlock == null ? "*" : stationBlock.toString());
        CacheEntry cached = CANDIDATE_CACHE.get(cacheKey);
        if (cached != null && level.getGameTime() - cached.gameTime() <= CANDIDATE_CACHE_TICKS) {
            return cached.candidates();
        }
        List<EngagementAt> engagements = engagements(level, site, stationBlock);
        if (engagements.isEmpty()) return List.of();
        Set<UUID> assignedElsewhere = new java.util.HashSet<>();
        WorksiteRegister register = WorksiteRegister.get(level.getServer());
        for (Worksite other : register.all()) {
            if (other != site && other.driver() != null) assignedElsewhere.add(other.driver());
        }

        BlockPos reference = reference(level, site);
        LinkedHashMap<UUID, Mob> discovered = new LinkedHashMap<>();
        for (EngagementAt at : engagements) {
            for (LivingEntity entity : at.reservation().candidates(
                    level, at.station(), site.villageId())) {
                if (entity instanceof Mob mob && available(mob)) discovered.putIfAbsent(mob.getUUID(), mob);
            }
        }

        List<Candidate> out = new ArrayList<>();
        for (Mob mob : discovered.values()) {
            // Keep the current assignment visible even if another stale worksite happens to name
            // it; all other double-claims are omitted from this chooser.
            if (assignedElsewhere.contains(mob.getUUID())
                    && !mob.getUUID().equals(site.driver())) continue;
            ResourceLocation type = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
            if (type == null) continue;
            String name = mob.getName().getString();
            if (!mob.hasCustomName()) {
                name += " (" + mob.blockPosition().getX() + ", "
                        + mob.blockPosition().getZ() + ")";
            }
            out.add(new Candidate(mob.getUUID(), type, name, mob.blockPosition().immutable()));
        }
        out.sort(Comparator
                .comparingDouble((Candidate c) -> c.position().distSqr(reference))
                .thenComparing(Candidate::name)
                .thenComparing(c -> c.uuid().toString()));
        List<Candidate> answer = List.copyOf(out);
        CANDIDATE_CACHE.put(cacheKey, new CacheEntry(level.getGameTime(), answer));
        if (CANDIDATE_CACHE.size() > 256) {
            long cutoff = level.getGameTime() - CANDIDATE_CACHE_TICKS * 4L;
            CANDIDATE_CACHE.entrySet().removeIf(entry -> entry.getValue().gameTime() < cutoff);
        }
        return answer;
    }

    /** Exact assignment when present, otherwise the nearest viable unclaimed candidate. */
    @Nullable
    public static Mob resolve(ServerLevel level, Worksite site,
                              ReservationSpec reservation, BlockPos station,
                              com.aetherianartificer.townstead.pheno.reservation.ReservationScope scope,
                              @Nullable LivingEntity worker) {
        return resolve(level, site, reservation, station, scope, worker,
                com.aetherianartificer.townstead.work.order.Order.Operation.AUTOMATIC, null);
    }

    /** Resolves an order's operation override before falling back to the worksite policy. */
    @Nullable
    public static Mob resolve(ServerLevel level, Worksite site,
                              ReservationSpec reservation, BlockPos station,
                              com.aetherianartificer.townstead.pheno.reservation.ReservationScope scope,
                              @Nullable LivingEntity worker,
                              com.aetherianartificer.townstead.work.order.Order.Operation operation,
                              @Nullable UUID operator) {
        if (level == null || site == null || reservation == null) return null;
        if (operation == com.aetherianartificer.townstead.work.order.Order.Operation.WORKER) {
            return worker instanceof Mob mob && available(mob)
                    && !com.aetherianartificer.townstead.pheno.reservation.Reservations
                    .isReservedByOther(scope, mob)
                    && reservation.accepts(level, station, site.villageId(), mob, worker)
                    ? mob : null;
        }
        if (operation == com.aetherianartificer.townstead.work.order.Order.Operation.ENTITY) {
            net.minecraft.world.entity.Entity entity = operator == null ? null : level.getEntity(operator);
            return entity instanceof Mob mob && available(mob)
                    && !com.aetherianartificer.townstead.pheno.reservation.Reservations
                    .isReservedByOther(scope, mob)
                    && reservation.accepts(level, station, site.villageId(), mob, worker)
                    ? mob : null;
        }
        if (site.driver() != null) {
            net.minecraft.world.entity.Entity entity = level.getEntity(site.driver());
            return entity instanceof Mob mob && available(mob)
                    && !com.aetherianartificer.townstead.pheno.reservation.Reservations
                    .isReservedByOther(scope, mob)
                    && reservation.accepts(level, station, site.villageId(), mob, worker) ? mob : null;
        }
        Set<UUID> assignedElsewhere = new java.util.HashSet<>();
        for (Worksite other : WorksiteRegister.get(level.getServer()).all()) {
            if (other != site && other.driver() != null) assignedElsewhere.add(other.driver());
        }
        List<LivingEntity> available = new ArrayList<>();
        for (LivingEntity entity : reservation.candidates(level, station, site.villageId(), worker)) {
            if (entity instanceof Mob mob && available(mob)
                    && !com.aetherianartificer.townstead.pheno.reservation.Reservations
                    .isReservedByOther(scope, mob)
                    && !assignedElsewhere.contains(mob.getUUID())) available.add(mob);
        }
        return reservation.prioritize(level, station, available).stream()
                .filter(Mob.class::isInstance).map(Mob.class::cast).findFirst().orElse(null);
    }

    /** Host-level availability only; authored Pheno selectors own all semantic eligibility. */
    public static boolean available(Mob mob) {
        return mob != null && mob.isAlive();
    }

    private static BlockPos reference(ServerLevel level, Worksite site) {
        if (site.villageId() != Worksite.NO_VILLAGE) {
            Village village = VillageManager.get(level).getOrEmpty(site.villageId()).orElse(null);
            if (village != null) {
                net.minecraft.core.Vec3i center = village.getCenter();
                return new BlockPos(center.getX(), center.getY(), center.getZ());
            }
        }
        Set<Long> extent = Worksites.extentOf(level, site);
        return extent.isEmpty() ? site.key().pos() : BlockPos.of(extent.iterator().next());
    }
}
