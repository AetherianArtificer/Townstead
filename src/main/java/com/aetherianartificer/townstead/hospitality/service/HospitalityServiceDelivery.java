package com.aetherianartificer.townstead.hospitality.service;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/** Bridges foreign requests into existing pantry, production, and physical-delivery work. */
public final class HospitalityServiceDelivery {
    private static final long SNAPSHOT_TICKS = 10L;
    private static final long CLAIM_TICKS = 200L;
    private static final ServiceClaimLedger CLAIMS = new ServiceClaimLedger();
    private static final Map<UUID, Snapshot> SNAPSHOTS = new HashMap<>();

    private HospitalityServiceDelivery() {}

    public static Set<ResourceLocation> demandedProducts(ServerLevel level, VillagerEntityMCA worker,
                                                          Set<Long> worksiteBounds) {
        Set<ResourceLocation> out = new LinkedHashSet<>();
        for (Candidate candidate : candidates(level, worker, worksiteBounds)) {
            out.add(candidate.request().product().item());
            out.add(candidate.request().product().product());
        }
        return Set.copyOf(out);
    }

    public static @Nullable BlockPos findTarget(ServerLevel level, VillagerEntityMCA worker,
                                                 Set<Long> worksiteBounds,
                                                 Predicate<ItemStack> carriedMatcher,
                                                 Set<Long> rejected) {
        return candidates(level, worker, worksiteBounds).stream()
                .filter(candidate -> rejected == null
                        || !rejected.contains(candidate.request().delivery().asLong()))
                .filter(candidate -> carries(worker, carriedMatcher, candidate))
                .min(Comparator
                        .comparingInt((Candidate candidate) -> -candidate.request().priority())
                        .thenComparingDouble(candidate -> worker.distanceToSqr(
                                candidate.request().delivery().getX() + 0.5,
                                candidate.request().delivery().getY() + 0.5,
                                candidate.request().delivery().getZ() + 0.5))
                        .thenComparing(candidate -> candidate.request().key().request()))
                .map(candidate -> candidate.request().delivery())
                .orElse(null);
    }

    public static @Nullable BlockPos findPreparationTarget(ServerLevel level,
                                                            VillagerEntityMCA worker,
                                                            Set<Long> worksiteBounds,
                                                            Predicate<ItemStack> carriedMatcher,
                                                            Set<Long> rejected) {
        return preparationCandidates(level, worker, worksiteBounds, carriedMatcher).stream()
                .filter(candidate -> rejected == null
                        || !rejected.contains(candidate.preparation().position().asLong()))
                .min(Comparator
                        .comparingInt((PreparationCandidate candidate) ->
                                -candidate.request().priority())
                        .thenComparingDouble(candidate -> worker.distanceToSqr(
                                candidate.preparation().position().getX() + 0.5,
                                candidate.preparation().position().getY() + 0.5,
                                candidate.preparation().position().getZ() + 0.5))
                        .thenComparing(candidate -> candidate.request().key().request()))
                .map(candidate -> candidate.preparation().position())
                .orElse(null);
    }

    public static boolean isTarget(ServerLevel level, VillagerEntityMCA worker, Set<Long> bounds,
                                   BlockPos target) {
        if (target == null) return false;
        for (Candidate candidate : candidates(level, worker, bounds)) {
            if (target.equals(candidate.request().delivery())) return true;
        }
        return false;
    }

    public static boolean isPreparationTarget(ServerLevel level, VillagerEntityMCA worker,
                                               Set<Long> bounds, BlockPos target,
                                               Predicate<ItemStack> carriedMatcher) {
        if (target == null) return false;
        for (PreparationCandidate candidate : preparationCandidates(
                level, worker, bounds, carriedMatcher)) {
            if (target.equals(candidate.preparation().position())) return true;
        }
        return false;
    }

    /** Provider commits the exact inventory stack; Townstead never performs a second shrink. */
    public static int deliverMatchingAt(ServerLevel level, VillagerEntityMCA worker,
                                        Set<Long> worksiteBounds, BlockPos target,
                                        Predicate<ItemStack> carriedMatcher) {
        long now = level.getGameTime();
        for (Candidate candidate : candidates(level, worker, worksiteBounds)) {
            ServiceRequest request = candidate.request();
            if (!target.equals(request.delivery())) continue;
            for (int slot = 0; slot < worker.getInventory().getContainerSize(); slot++) {
                ItemStack carried = worker.getInventory().getItem(slot);
                if (carried.isEmpty() || !carriedMatcher.test(carried)
                        || !candidate.provider().accepts(request, carried)) continue;
                ServiceClaim claim = CLAIMS.tryClaim(request, worker.getUUID(), now, CLAIM_TICKS);
                if (claim == null) return 0;
                try {
                    ServiceFulfillment result = candidate.provider().fulfill(
                            level, claim, carried, worker, null);
                    if (result.status() != ServiceFulfillment.Status.SUCCESS) return 0;
                    ItemStack returned = result.returned();
                    if (!returned.isEmpty()) worker.getInventory().addItem(returned);
                    worker.getInventory().setChanged();
                    invalidate(worker);
                    return result.accepted();
                } finally {
                    CLAIMS.release(request.key(), worker.getUUID());
                }
            }
        }
        return 0;
    }

    /** Provider mutates the raw input and returns a new exact serving; Townstead only inventories it. */
    public static ServicePreparationResult prepareMatchingAt(ServerLevel level,
                                                               VillagerEntityMCA worker,
                                                               Set<Long> worksiteBounds,
                                                               BlockPos target,
                                                               Predicate<ItemStack> carriedMatcher) {
        for (PreparationCandidate candidate : preparationCandidates(
                level, worker, worksiteBounds, carriedMatcher)) {
            if (!target.equals(candidate.preparation().position())) continue;
            for (int slot = 0; slot < worker.getInventory().getContainerSize(); slot++) {
                ItemStack offered = worker.getInventory().getItem(slot);
                if (offered.isEmpty() || !carriedMatcher.test(offered)) continue;
                int before = offered.getCount();
                ServicePreparationResult result = candidate.provider().prepare(level,
                        candidate.preparation(), candidate.request(), offered, worker);
                if (result.status() != ServicePreparationResult.Status.SUCCESS) return result;
                int accepted = before - offered.getCount();
                if (accepted != result.accepted() || accepted < 1) {
                    return ServicePreparationResult.rejected(
                            ServicePreparationResult.Status.ERROR,
                            "provider result did not match committed input quantity");
                }
                ItemStack overflow = worker.getInventory().addItem(result.output());
                if (!overflow.isEmpty()) worker.spawnAtLocation(overflow);
                worker.getInventory().setChanged();
                invalidate(worker);
                return result;
            }
        }
        return ServicePreparationResult.rejected(ServicePreparationResult.Status.CANCELLED,
                "preparation target is no longer actionable");
    }

    public static List<ServiceFollowup> followups(ServerLevel level, VillagerEntityMCA worker,
                                                   Set<Long> worksiteBounds) {
        List<ServiceFollowup> out = new ArrayList<>();
        for (SiteCandidate site : sites(level, worker, worksiteBounds)) {
            out.addAll(site.provider().followups(level, site.site()));
        }
        return List.copyOf(out);
    }

    public static ServiceFollowupCompletion completeFollowup(ServerLevel level,
                                                              VillagerEntityMCA worker,
                                                              ServiceFollowup followup) {
        HospitalityServiceProvider provider = HospitalityServiceProviders.get(followup.request().provider());
        if (provider == null) {
            return ServiceFollowupCompletion.rejected(ServiceFollowupCompletion.Status.UNSUPPORTED,
                    "service provider is not registered");
        }
        ServiceFollowupCompletion result = provider.completeFollowup(level, followup, worker);
        if (result.status() == ServiceFollowupCompletion.Status.SUCCESS) invalidate(worker);
        return result;
    }

    private static boolean carries(VillagerEntityMCA worker, Predicate<ItemStack> matcher,
                                   Candidate candidate) {
        for (int slot = 0; slot < worker.getInventory().getContainerSize(); slot++) {
            ItemStack stack = worker.getInventory().getItem(slot);
            if (!stack.isEmpty() && matcher.test(stack)
                    && candidate.provider().accepts(candidate.request(), stack)) return true;
        }
        return false;
    }

    private static List<Candidate> candidates(ServerLevel level, VillagerEntityMCA worker,
                                              Set<Long> worksiteBounds) {
        Snapshot snapshot = snapshot(level, worker, worksiteBounds);
        return snapshot.candidates();
    }

    private static List<PreparationCandidate> preparationCandidates(
            ServerLevel level, VillagerEntityMCA worker, Set<Long> worksiteBounds,
            Predicate<ItemStack> carriedMatcher) {
        List<PreparationCandidate> out = new ArrayList<>();
        for (Candidate candidate : candidates(level, worker, worksiteBounds)) {
            for (int slot = 0; slot < worker.getInventory().getContainerSize(); slot++) {
                ItemStack offered = worker.getInventory().getItem(slot);
                if (offered.isEmpty() || !carriedMatcher.test(offered)) continue;
                for (ServicePreparation preparation : candidate.provider().preparations(
                        level, candidate.site(), candidate.request(), offered, worker)) {
                    out.add(new PreparationCandidate(candidate.provider(), candidate.site(),
                            candidate.request(), preparation));
                }
            }
        }
        return List.copyOf(out);
    }

    private static List<SiteCandidate> sites(ServerLevel level, VillagerEntityMCA worker,
                                             Set<Long> worksiteBounds) {
        Snapshot snapshot = snapshot(level, worker, worksiteBounds);
        return snapshot.sites();
    }

    private static synchronized Snapshot snapshot(ServerLevel level, VillagerEntityMCA worker,
                                                  Set<Long> worksiteBounds) {
        if (level == null || worker == null || worksiteBounds == null || worksiteBounds.isEmpty()) {
            return Snapshot.EMPTY;
        }
        long now = level.getGameTime();
        int boundsHash = worksiteBounds.hashCode();
        Snapshot cached = SNAPSHOTS.get(worker.getUUID());
        if (cached != null && cached.dimension().equals(level.dimension().location())
                && cached.boundsHash() == boundsHash && now <= cached.createdAt() + SNAPSHOT_TICKS) {
            return cached;
        }

        Bounds geometry = bounds(worksiteBounds);
        List<SiteCandidate> sites = new ArrayList<>();
        List<Candidate> requests = new ArrayList<>();
        for (HospitalityServiceProvider provider : HospitalityServiceProviders.all()) {
            for (ServiceSite site : provider.discover(level, geometry.center(), geometry.radius())) {
                if (!overlaps(site, worksiteBounds)) continue;
                SiteCandidate siteCandidate = new SiteCandidate(provider, site);
                sites.add(siteCandidate);
                for (ServiceRequest request : provider.requests(level, site)) {
                    if (!request.expired(now)) requests.add(new Candidate(provider, site, request));
                }
            }
        }
        Snapshot next = new Snapshot(level.dimension().location(), now, boundsHash,
                List.copyOf(sites), List.copyOf(requests));
        SNAPSHOTS.put(worker.getUUID(), next);
        CLAIMS.prune(now);
        return next;
    }

    private static boolean overlaps(ServiceSite site, Set<Long> worksiteBounds) {
        if (worksiteBounds.contains(site.anchor().asLong())) return true;
        for (long packed : site.bounds()) if (worksiteBounds.contains(packed)) return true;
        return false;
    }

    private static Bounds bounds(Set<Long> cells) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (long packed : cells) {
            BlockPos pos = BlockPos.of(packed);
            minX = Math.min(minX, pos.getX()); minY = Math.min(minY, pos.getY()); minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX()); maxY = Math.max(maxY, pos.getY()); maxZ = Math.max(maxZ, pos.getZ());
        }
        BlockPos center = new BlockPos((minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2);
        int radius = Math.max(8, Math.max(maxX - minX, maxZ - minZ) / 2 + 8);
        return new Bounds(center, radius);
    }

    private static synchronized void invalidate(VillagerEntityMCA worker) {
        SNAPSHOTS.remove(worker.getUUID());
    }

    private record Bounds(BlockPos center, int radius) {}
    private record SiteCandidate(HospitalityServiceProvider provider, ServiceSite site) {}
    private record Candidate(HospitalityServiceProvider provider, ServiceSite site,
                             ServiceRequest request) {}
    private record PreparationCandidate(HospitalityServiceProvider provider, ServiceSite site,
                                        ServiceRequest request,
                                        ServicePreparation preparation) {}
    private record Snapshot(ResourceLocation dimension, long createdAt, int boundsHash,
                            List<SiteCandidate> sites, List<Candidate> candidates) {
        private static final Snapshot EMPTY = new Snapshot(
                ResourceLocation.tryParse("minecraft:overworld"), 0L, 0, List.of(), List.of());
    }
}
