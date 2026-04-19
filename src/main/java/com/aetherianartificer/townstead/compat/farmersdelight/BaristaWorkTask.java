package com.aetherianartificer.townstead.compat.farmersdelight;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.ai.work.WorkBuildingNav;
import com.aetherianartificer.townstead.ai.work.WorkSiteRef;
import com.aetherianartificer.townstead.ai.work.producer.ProducerBlockedReason;
import com.aetherianartificer.townstead.ai.work.producer.ProducerRecipe;
import com.aetherianartificer.townstead.ai.work.producer.ProducerStationClaims;
import com.aetherianartificer.townstead.ai.work.producer.ProducerStationSessions;
import com.aetherianartificer.townstead.ai.work.producer.ProducerStationState;
import com.aetherianartificer.townstead.ai.work.producer.ProducerWorkTask;
import com.aetherianartificer.townstead.compat.ModCompat;
import com.aetherianartificer.townstead.compat.farmersdelight.cook.IngredientResolver;
import com.aetherianartificer.townstead.compat.farmersdelight.cook.ModRecipeRegistry;
import com.aetherianartificer.townstead.compat.farmersdelight.cook.ModRecipeRegistry.DiscoveredRecipe;
import com.aetherianartificer.townstead.compat.farmersdelight.cook.ModRecipeRegistry.StationType;
import com.aetherianartificer.townstead.compat.farmersdelight.cook.StationHandler;
import com.aetherianartificer.townstead.compat.farmersdelight.cook.StationHandler.StationSlot;
import com.aetherianartificer.townstead.hunger.CookProgressData;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Building;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class BaristaWorkTask extends ProducerWorkTask {

    private static final long ROOM_BOUNDS_CACHE_TICKS = 80L;

    // Subclass-only state
    private @Nullable StationType stationType;

    // Cafe bounds cache
    private Set<Long> cachedCafeNavigationArea = Set.of();
    private @Nullable BlockPos cachedCafeNavigationAnchor = null;
    private long cachedCafeNavigationUntil = 0L;
    private WorkBuildingNav.Snapshot cachedCafeSnapshot = WorkBuildingNav.Snapshot.EMPTY;

    public BaristaWorkTask() {
        super();
    }

    // ── Identity / guards ──

    @Override
    protected boolean isTaskEnabled() {
        if (!ModCompat.isLoaded("rusticdelight")) return false;
        return TownsteadConfig.isTownsteadCookEnabled();
    }

    @Override
    protected boolean isEligibleVillager(ServerLevel level, VillagerEntityMCA villager) {
        VillagerProfession profession = villager.getVillagerData().getProfession();
        if (!FarmersDelightBaristaAssignment.isBaristaProfession(profession)) return false;
        return FarmersDelightBaristaAssignment.canVillagerWorkAsBarista(level, villager);
    }

    // ── Worksite ──

    @Override
    protected @Nullable WorkSiteRef resolveWorksite(ServerLevel level, VillagerEntityMCA villager) {
        Set<Long> bounds = activeCafeStorageBounds(villager);
        if (bounds.isEmpty()) return null;
        BlockPos reference = activeCafeReference(villager);
        return WorkSiteRef.building(reference, bounds);
    }

    @Override
    protected boolean isVillagerAtWorksite(ServerLevel level, VillagerEntityMCA villager) {
        return isVillagerInActiveCafe(villager);
    }

    @Override
    protected @Nullable BlockPos resolveWorksiteTarget(ServerLevel level, VillagerEntityMCA villager, long gameTime, WorkSiteRef site) {
        WorkBuildingNav.Snapshot cafeSnapshot = activeCafeSnapshot(level, villager);
        return currentOrNewCafeWorksiteTarget(villager, gameTime, cafeSnapshot);
    }

    @Override
    protected BlockPos worksiteReference(VillagerEntityMCA villager) {
        return activeCafeReference(villager);
    }

    @Override
    protected @Nullable BlockPos refreshStandPosition(ServerLevel level, VillagerEntityMCA villager, @Nullable BlockPos stationAnchor) {
        if (stationAnchor == null) return null;
        return findCafeStandingPosition(level, villager, stationAnchor, activeCafeSnapshot(level, villager));
    }

    // ── Station acquisition ──

    @Override
    protected @Nullable ProducerStationSelection selectStation(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        Set<Long> cafeBounds = activeCafeStorageBounds(villager);
        WorkBuildingNav.Snapshot cafeSnapshot = activeCafeSnapshot(level, villager);
        List<StationSlot> stations = cafeSnapshot.stations().stream()
                .filter(s -> s.type() == StationType.HOT_STATION || s.type() == StationType.FIRE_STATION)
                .toList();
        if (stations.isEmpty()) {
            debugChat(level, villager, "ACQUIRE:no stations found in cafe (" + cafeBounds.size() + " bounds)");
            setBlocked(level, villager, gameTime, ProducerBlockedReason.NO_WORKSITE, "");
            return null;
        }
        ProducerStationIndex.Selection best = ProducerStationIndex.chooseBaristaSelection(
                level, villager, cafeSnapshot, cafeBounds, abandonedUntilByStation, gameTime, recipeCooldownUntil);
        if (best == null) return null;

        stationType = best.station().type();
        debugChat(level, villager, "ACQUIRE:" + stationType.name()
                + " at " + best.station().pos().getX() + "," + best.station().pos().getY() + "," + best.station().pos().getZ());
        return new ProducerStationSelection(best.station().pos(), best.standPos(), best.recipe());
    }

    @Override
    protected void claimStation(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (stationAnchor == null) return;
        ProducerStationClaims.tryClaim(level, villager.getUUID(), stationAnchor, gameTime + MAX_DURATION + 20L);
    }

    @Override
    protected void releaseStationClaim(ServerLevel level, VillagerEntityMCA villager, @Nullable BlockPos pos) {
        if (pos == null) return;
        ProducerStationClaims.release(level, villager.getUUID(), pos);
    }

    // ── Reconcile ──

    @Override
    protected ProducerStationState classifyStation(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (stationAnchor == null || stationType == null) return ProducerStationState.BLOCKED;
        ProducerStationSessions.SessionSnapshot session = ProducerStationSessions.snapshot(level, stationAnchor);
        if (activeRecipe == null && session != null) {
            activeRecipe = ProducerWorkSupport.findSessionRecipe(ProducerRole.BARISTA, level, session, stationType);
        }
        return StationHandler.classifyProducerStation(level, villager, stationAnchor, stationType, fdRecipe(), session);
    }

    @Override
    protected boolean cleanupForeignStation(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (stationAnchor == null || stationType == null) return false;
        Set<Long> cafeBounds = activeCafeStorageBounds(villager);
        boolean cleaned = StationHandler.cleanupForeignProducerStation(level, villager, stationAnchor, stationType, cafeBounds);
        return cleaned && !StationHandler.stationHasAnyContents(level, stationAnchor, stationType);
    }

    // ── Recipe / gather / produce / collect ──

    @Override
    protected @Nullable ProducerRecipe pickRecipe(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (stationAnchor == null || stationType == null) return null;
        if (!StationHandler.isStation(level, stationAnchor)) return null;
        Set<Long> cafeBounds = activeCafeStorageBounds(villager);
        DiscoveredRecipe recipe = ProducerWorkSupport.pickRecipe(
                ProducerRole.BARISTA, level, villager, stationType, stationAnchor, cafeBounds, recipeCooldownUntil);
        if (recipe == null) {
            int available = ModRecipeRegistry.getBeverageRecipesForStation(level, stationType).size();
            debugChat(level, villager, "SELECT:no beverage recipe for " + stationType.name()
                    + " (candidates=" + available + "), rotating");
        } else {
            debugChat(level, villager, "SELECT:" + recipe.output() + " tier=" + recipe.tier());
        }
        return recipe;
    }

    @Override
    protected GatherResult gatherInputs(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (activeRecipe == null || stationAnchor == null || stationType == null) return GatherResult.fail(null);
        DiscoveredRecipe recipe = fdRecipe();
        Set<Long> cafeBounds = activeCafeStorageBounds(villager);
        IngredientResolver.PullResult pullResult = IngredientResolver.pullAndConsumeDetailed(
                level, villager, recipe, stationAnchor, stationType, stagedInputs, cafeBounds);
        if (!pullResult.success()) {
            String recipeName = townstead$itemDisplayName(level, recipe.output());
            String missing = IngredientResolver.describeMissingRequirements(level, villager, recipe, stationAnchor, cafeBounds);
            if (missing == null || missing.isBlank()) missing = pullResult.detail();
            String detail = (missing == null || missing.isBlank()) ? recipeName : missing;
            return GatherResult.fail(detail);
        }

        debugChat(level, villager, "GATHER:success for " + recipe.output());
        return GatherResult.ok();
    }

    @Override
    protected void rollbackGather(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        IngredientResolver.rollbackStagedInputs(level, villager, stationAnchor, stagedInputs);
    }

    @Override
    protected boolean beginProduce(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (activeRecipe == null) return false;
        DiscoveredRecipe recipe = fdRecipe();
        produceDoneTick = gameTime + recipe.cookTimeTicks();
        return true;
    }

    @Override
    protected boolean isProduceDone(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (activeRecipe == null) return true;
        DiscoveredRecipe recipe = fdRecipe();

        if (stationType == StationType.HOT_STATION
                && stationAnchor != null
                && !ProducerOutputHelper.hotStationOutputCollectible(level, stationAnchor, recipe)
                && !StationHandler.stationHasCollectibleOutput(level, stationAnchor, ModRecipeRegistry.allOutputIds(level))) {
            return false;
        }

        return true;
    }

    @Override
    protected CollectResult collectFromStation(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        Set<Long> cafeBounds = activeCafeStorageBounds(villager);
        Set<ResourceLocation> outputIds = ModRecipeRegistry.allOutputIds(level);
        boolean collected = ProducerOutputHelper.collectSurfaceDrops(level, villager, stationAnchor, cafeBounds, outputIds);

        if (stationType == StationType.HOT_STATION) {
            ProducerOutputHelper.CollectResult result = ProducerOutputHelper.collectHotStationOutputs(
                    level, villager, stationAnchor, fdRecipe(), cafeBounds, outputIds, true);
            if (result.shouldWait()) return CollectResult.waiting(false);
            collected |= result.collected();
        }

        if (stationType == StationType.FIRE_STATION && !collected) {
            return CollectResult.waiting(false);
        }

        return collected ? CollectResult.ofCollected() : CollectResult.none();
    }

    @Override
    protected void storeOutputs(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        Set<Long> cafeBounds = activeCafeStorageBounds(villager);
        Set<ResourceLocation> outputIds = ModRecipeRegistry.allOutputIds(level);
        ProducerOutputHelper.finishCollectInventoryOutputs(level, villager, pendingOutput, stationAnchor, cafeBounds, outputIds);
    }

    @Override
    protected void awardProductionXp(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (activeRecipe == null) return;
        int xp = Math.max(1, activeRecipe.tier());
        //? if neoforge {
        CompoundTag data = villager.getData(Townstead.HUNGER_DATA);
        //?} else {
        /*CompoundTag data = villager.getPersistentData().getCompound("townstead_hunger");
        *///?}
        CookProgressData.addXp(data, xp, level.getGameTime());
        //? if neoforge {
        villager.setData(Townstead.HUNGER_DATA, data);
        //?} else {
        /*villager.getPersistentData().put("townstead_hunger", data);
        *///?}
    }

    // ── Hooks ──

    @Override
    protected void onProduceTick(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (stationType == StationType.FIRE_STATION && stationAnchor != null) {
            Set<ResourceLocation> outputIds = ModRecipeRegistry.allOutputIds(level);
            List<ItemStack> drops = StationHandler.collectSurfaceCookDrops(level, stationAnchor, outputIds);
            Set<Long> cafeBounds = activeCafeStorageBounds(villager);
            for (ItemStack drop : drops) {
                IngredientResolver.storeOutputInCookStorage(level, villager, drop, stationAnchor, cafeBounds);
                if (!drop.isEmpty()) {
                    ItemStack remainder = villager.getInventory().addItem(drop);
                    if (!remainder.isEmpty()) {
                        ItemEntity entity = new ItemEntity(level, villager.getX(), villager.getY() + 0.25, villager.getZ(), remainder);
                        entity.setPickUpDelay(0);
                        level.addFreshEntity(entity);
                    }
                }
            }
        }
    }

    @Override
    protected boolean mustWaitBeyondCollectTimeout(ServerLevel level, VillagerEntityMCA villager) {
        return stationType == StationType.HOT_STATION;
    }

    @Override
    protected void onSessionRefresh(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (stationAnchor == null || activeRecipe == null) return;
        DiscoveredRecipe recipe = fdRecipe();
        ProducerStationSessions.beginOrRefresh(
                level, villager.getUUID(), stationAnchor,
                recipe.id(), recipe.output(), recipe.outputCount(),
                stagedInputs, gameTime + STATION_SESSION_LEASE_TICKS);
    }

    @Override
    protected void onSessionRelease(ServerLevel level, VillagerEntityMCA villager, @Nullable BlockPos pos, long gameTime) {
        if (pos == null) return;
        ProducerStationSessions.release(level, villager.getUUID(), pos);
    }

    @Override
    protected void onOpportunisticSweep(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        ProducerOutputHelper.sweepNearbyOutputs(
                level, villager, stationAnchor,
                activeCafeStorageBounds(villager),
                ModRecipeRegistry.allOutputIds(level));
    }

    @Override
    protected void onStop(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        stationType = null;
        cachedCafeNavigationArea = Set.of();
        cachedCafeNavigationAnchor = null;
        cachedCafeNavigationUntil = 0L;
        cachedCafeSnapshot = WorkBuildingNav.Snapshot.EMPTY;
    }

    @Override
    protected void onClearAll(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        stationType = null;
        cachedCafeNavigationArea = Set.of();
        cachedCafeNavigationAnchor = null;
        cachedCafeNavigationUntil = 0L;
        cachedCafeSnapshot = WorkBuildingNav.Snapshot.EMPTY;
    }

    @Override
    protected void playGatherSound(ServerLevel level, VillagerEntityMCA villager) {
        if (stationAnchor == null || stationType == null) return;
        level.playSound(null, stationAnchor, SoundEvents.CAMPFIRE_CRACKLE, net.minecraft.sounds.SoundSource.BLOCKS, 0.35f, 1.0f);
    }

    @Override
    protected void announceBlocked(ServerLevel level, VillagerEntityMCA villager, long gameTime,
                                   ProducerBlockedReason reason, @Nullable String detail) {
        if (reason == ProducerBlockedReason.NONE || reason == ProducerBlockedReason.NO_RECIPE) return;
        if (!TownsteadConfig.isBaristaRequestChatEnabled()) return;
        if (gameTime < nextRequestTick) return;
        if (level.getNearestPlayer(villager, REQUEST_RANGE) == null) return;
        if (reason == ProducerBlockedReason.UNREACHABLE
                && !shouldAnnounceBlockedNavigation(level, villager, activeWorkTarget(level, villager))) {
            return;
        }
        switch (reason) {
            case NO_WORKSITE -> villager.sendChatToAllAround("dialogue.chat.barista_request.no_cafe/" + (1 + level.random.nextInt(4)));
            case NO_INGREDIENTS -> {
                if (detail != null && !detail.isBlank()) {
                    villager.sendChatToAllAround("dialogue.chat.barista_request.no_ingredients_item", detail);
                } else {
                    villager.sendChatToAllAround("dialogue.chat.barista_request.no_ingredients/" + (1 + level.random.nextInt(4)));
                }
            }
            case NO_STORAGE -> villager.sendChatToAllAround("dialogue.chat.barista_request.no_storage/" + (1 + level.random.nextInt(4)));
            case UNREACHABLE -> villager.sendChatToAllAround("dialogue.chat.barista_request.unreachable/" + (1 + level.random.nextInt(4)));
            default -> {}
        }
        nextRequestTick = gameTime + Math.max(200, TownsteadConfig.BARISTA_REQUEST_INTERVAL_TICKS.get());
    }

    // ── Subclass helpers ──

    /** Typed downcast — base stores {@code activeRecipe} as {@link ProducerRecipe}. */
    private @Nullable DiscoveredRecipe fdRecipe() {
        return (DiscoveredRecipe) activeRecipe;
    }

    /** Resolve the set of blocks belonging to this villager's assigned cafe. */
    private Set<Long> activeCafeStorageBounds(VillagerEntityMCA villager) {
        if (!(villager.level() instanceof ServerLevel level)) return Set.of();
        return FarmersDelightBaristaAssignment.assignedCafeBounds(level, villager);
    }

    /** Cache the expensive cafe bounds / walkable-interior snapshot for ROOM_BOUNDS_CACHE_TICKS. */
    private void cacheCafeNavigationArea(BlockPos anchor, long gameTime, Set<Long> bounds) {
        cachedCafeNavigationAnchor = anchor == null ? null : anchor.immutable();
        cachedCafeNavigationArea = bounds == null ? Set.of() : bounds;
        cachedCafeNavigationUntil = gameTime + ROOM_BOUNDS_CACHE_TICKS;
    }

    /** Returns a cached WorkBuildingNav.Snapshot for the active cafe (rebuilds at TTL expiry). */
    private WorkBuildingNav.Snapshot activeCafeSnapshot(ServerLevel level, VillagerEntityMCA villager) {
        BlockPos anchor = activeCafeReference(villager);
        long gameTime = level.getGameTime();
        if (anchor != null && cachedCafeNavigationAnchor != null
                && anchor.equals(cachedCafeNavigationAnchor)
                && gameTime <= cachedCafeNavigationUntil
                && !cachedCafeSnapshot.walkableInterior().isEmpty()) {
            return cachedCafeSnapshot;
        }
        Set<Long> assigned = activeCafeStorageBounds(villager);
        WorkBuildingNav.Snapshot snapshot = WorkBuildingNav.snapshot(level, assigned, anchor);
        cachedCafeSnapshot = snapshot;
        cacheCafeNavigationArea(anchor, gameTime, snapshot.walkableInterior());
        return snapshot;
    }

    private Optional<Building> activeCafeBuilding(ServerLevel level, VillagerEntityMCA villager) {
        return FarmersDelightBaristaAssignment.assignedCafe(level, villager);
    }

    /** Pick the best reference block for cafe queries: assigned center, station anchor, or villager. */
    private BlockPos activeCafeReference(VillagerEntityMCA villager) {
        if (villager.level() instanceof ServerLevel level) {
            Optional<Building> assigned = activeCafeBuilding(level, villager);
            if (assigned.isPresent()) {
                BlockPos center = assigned.get().getCenter();
                if (center != null) return center;
                for (BlockPos bp : (Iterable<BlockPos>) assigned.get().getBlockPosStream()::iterator) {
                    return bp.immutable();
                }
            }
        }
        if (stationAnchor != null) return stationAnchor;
        return villager.blockPosition();
    }

    /** True when the villager is inside the active cafe (by Building containment or walkable-interior). */
    private boolean isVillagerInActiveCafe(VillagerEntityMCA villager) {
        if (!(villager.level() instanceof ServerLevel level)) return false;
        Optional<Building> assigned = activeCafeBuilding(level, villager);
        if (assigned.isPresent() && buildingContainsVillager(assigned.get(), villager)) return true;
        return WorkBuildingNav.isInsideOrOnStationStand(activeCafeSnapshot(level, villager), villager.blockPosition());
    }

    private boolean buildingContainsVillager(Building building, VillagerEntityMCA villager) {
        BlockPos pos = villager.blockPosition();
        if (building.containsPos(pos)) return true;
        if (building.containsPos(pos.below())) return true;
        return building.containsPos(pos.above());
    }

    /** Re-use the existing worksite target if still valid, otherwise pick a nearest non-blacklisted stand/approach. */
    private @Nullable BlockPos currentOrNewCafeWorksiteTarget(
            VillagerEntityMCA villager, long gameTime, WorkBuildingNav.Snapshot cafeSnapshot) {
        if (currentWorksiteTarget != null
                && !worksiteTargetFailures.isBlacklisted(currentWorksiteTarget, gameTime)) {
            return currentWorksiteTarget;
        }

        if (villager.level() instanceof ServerLevel level) {
            Optional<Building> assigned = activeCafeBuilding(level, villager);
            if (assigned.isPresent() && !buildingContainsVillager(assigned.get(), villager)) {
                BlockPos buildingTarget = pickCafeBuildingEntryTarget(assigned.get(), level, villager, gameTime);
                if (buildingTarget != null) {
                    currentWorksiteTargetKind = "building";
                    currentWorksiteTarget = buildingTarget.immutable();
                    return currentWorksiteTarget;
                }
            }
        }

        List<BlockPos> standCandidates = cafeSnapshot.stationStandPositions().values().stream()
                .flatMap(List::stream)
                .filter(pos -> !worksiteTargetFailures.isBlacklisted(pos, gameTime))
                .distinct()
                .sorted(Comparator.comparingDouble(pos ->
                        villager.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)))
                .toList();
        if (!standCandidates.isEmpty()) {
            currentWorksiteTargetKind = "stand";
            currentWorksiteTarget = standCandidates.get(0);
            return currentWorksiteTarget;
        }

        List<BlockPos> fallbackCandidates = cafeSnapshot.approachTargets().stream()
                .filter(pos -> !worksiteTargetFailures.isBlacklisted(pos, gameTime))
                .sorted(Comparator.comparingDouble(pos ->
                        villager.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)))
                .toList();
        if (fallbackCandidates.isEmpty()) {
            currentWorksiteTarget = null;
            return null;
        }
        currentWorksiteTargetKind = "fallback";
        currentWorksiteTarget = fallbackCandidates.get(0);
        return currentWorksiteTarget;
    }

    /** Pick a target inside the assigned cafe building when the villager isn't in it yet. */
    private @Nullable BlockPos pickCafeBuildingEntryTarget(Building building, ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        BlockPos center = building.getCenter();
        if (center != null && !worksiteTargetFailures.isBlacklisted(center, gameTime)) {
            return center.immutable();
        }

        if (building.getBuildingType().grouped()) {
            return center != null ? center.immutable() : null;
        }

        RandomSource random = level.getRandom();
        BlockPos pos0 = building.getPos0();
        BlockPos pos1 = building.getPos1();
        BlockPos diff = pos1.subtract(pos0);
        int margin = 2;
        BlockPos best = null;
        double bestDistanceSq = Double.POSITIVE_INFINITY;
        for (int attempt = 0; attempt < 16; attempt++) {
            BlockPos candidate = pos0.offset(new BlockPos(
                    random.nextInt(Math.max(1, diff.getX() - margin * 2)) + margin,
                    random.nextInt(Math.max(1, diff.getY() - margin * 2)) + margin,
                    random.nextInt(Math.max(1, diff.getZ() - margin * 2)) + margin
            ));
            if (level.canSeeSky(candidate)) continue;
            if (worksiteTargetFailures.isBlacklisted(candidate, gameTime)) continue;
            double distanceSq = villager.distanceToSqr(candidate.getX() + 0.5, candidate.getY() + 0.5, candidate.getZ() + 0.5);
            if (distanceSq < bestDistanceSq) {
                best = candidate.immutable();
                bestDistanceSq = distanceSq;
            }
        }
        return best;
    }

    /** Fresh stand position near the given anchor: prefer cafe snapshot, fall back to station-handler scan. */
    private @Nullable BlockPos findCafeStandingPosition(
            ServerLevel level,
            VillagerEntityMCA villager,
            BlockPos anchor,
            WorkBuildingNav.Snapshot cafeSnapshot
    ) {
        BlockPos stand = WorkBuildingNav.nearestStationStand(cafeSnapshot, villager, anchor);
        if (stand != null) return stand;
        BlockPos fallback = StationHandler.findStandingPosition(level, villager, anchor);
        if (fallback != null && cafeSnapshot.walkableInterior().contains(fallback.asLong())) {
            return fallback.immutable();
        }
        return null;
    }

    private static String townstead$itemDisplayName(ServerLevel level, ResourceLocation itemId) {
        //? if >=1.21 {
        var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(itemId);
        //?} else {
        /*var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(itemId);
        *///?}
        if (item == null) return itemId.getPath();
        return item.getDefaultInstance().getHoverName().getString();
    }
}
