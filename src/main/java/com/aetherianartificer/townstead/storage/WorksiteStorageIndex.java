package com.aetherianartificer.townstead.storage;


import com.aetherianartificer.townstead.work.recipe.RecipeIngredient;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.compat.thirst.ThirstCompatBridge;
import com.aetherianartificer.townstead.hunger.NearbyItemSources;
import com.aetherianartificer.townstead.storage.VillageAiBudget;
import com.aetherianartificer.townstead.storage.StorageSearchContext;
import com.aetherianartificer.townstead.storage.VillageStorageIndex;
import com.aetherianartificer.townstead.supply.SupplyLines;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

public final class WorksiteStorageIndex {
    private static final long SNAPSHOT_TTL_TICKS = 20L;
    private static final int REFRESH_BUDGET_PER_TICK = 2;
    private static final Map<SnapshotKey, Snapshot> SNAPSHOTS = new ConcurrentHashMap<>();

    private WorksiteStorageIndex() {}

    public static Snapshot snapshot(ServerLevel level, VillagerEntityMCA villager, Set<Long> kitchenBounds) {
        SnapshotKey key = SnapshotKey.create(level, villager, kitchenBounds);
        Snapshot current = SNAPSHOTS.get(key);
        long gameTime = level.getGameTime();
        if (current != null && current.validAt(gameTime)) {
            return current;
        }
        if (current != null && !VillageAiBudget.tryConsume(level, "kitchen-storage:" + key.boundsKey().cachedHash(), REFRESH_BUDGET_PER_TICK)) {
            return current;
        }
        Snapshot rebuilt = buildSnapshot(level, villager, kitchenBounds, gameTime);
        SNAPSHOTS.put(key, rebuilt);
        return rebuilt;
    }

    public static void invalidate(ServerLevel level) {
        String dimensionId = level.dimension().location().toString();
        SNAPSHOTS.keySet().removeIf(key -> key.dimensionId.equals(dimensionId));
        WorksiteStorageCandidateIndex.invalidate(level);
        VillageStorageIndex.invalidate(level);
    }

    public static void purgeExpired(long gameTime) {
        SNAPSHOTS.entrySet().removeIf(entry -> !entry.getValue().validAt(gameTime));
    }

    public static void clearAll() {
        SNAPSHOTS.clear();
    }

    public static int snapshotCount() {
        return SNAPSHOTS.size();
    }

    public static void invalidate(ServerLevel level, BlockPos changedPos) {
        if (level == null || changedPos == null) return;
        VillageStorageIndex.invalidate(level, changedPos);
        WorksiteStorageCandidateIndex.invalidate(level, changedPos);
        String dimensionId = level.dimension().location().toString();
        long changedKey = changedPos.asLong();
        SNAPSHOTS.entrySet().removeIf(entry -> entry.getKey().dimensionId.equals(dimensionId)
                && (entry.getKey().boundsKey.candidateSearchContains(changedKey)
                || entry.getValue().containsPosition(changedKey)));
    }

    private static Snapshot buildSnapshot(ServerLevel level, VillagerEntityMCA villager, Set<Long> kitchenBounds, long gameTime) {
        StorageSearchContext searchContext = new StorageSearchContext(level);
        List<Entry> entries = new ArrayList<>();
        Map<ResourceLocation, Integer> itemCounts = new HashMap<>();
        Set<Long> visited = new HashSet<>();
        StoragePreference preference = StoragePreference.forVillager(villager);

        List<CandidatePosition> candidates = new ArrayList<>();
        // The worksite's own shelves are the predictable first stop. External stores are fallback.
        for (BlockPos pos : candidateStoragePositions(level, kitchenBounds)) {
            candidates.add(new CandidatePosition(pos, StoragePreference.LOCAL_RANK));
        }
        for (var building : PreferredStorageBuildings.resolve(level, villager)) {
            int buildingRank = preference.buildingRank(building.getType());
            building.getBlockPosStream().forEach(pos ->
                    candidates.add(new CandidatePosition(pos.immutable(), buildingRank)));
        }
        if (candidates.isEmpty()) {
            return new Snapshot(List.of(), Map.of(), gameTime + SNAPSHOT_TTL_TICKS);
        }

        for (CandidatePosition candidate : candidates) {
            BlockPos pos = candidate.pos();
            if (!visited.add(pos.asLong())) continue;
            StorageSearchContext.ObservedBlock observed = searchContext.observe(pos);
            BlockEntity be = observed.blockEntity();
            if (be == null) continue;
            if (!StorageRoles.isStorageCandidate(level, observed.pos(), be, villager)) continue;
            Set<StorageRoleDef.Role> roles = StorageRoles.semanticRoles(observed.state());
            List<SlotView> slots = new ArrayList<>();
            boolean useHandlers = StorageInventoryPolicy.useItemHandlerView(be);
            if (be instanceof Container container) {
                for (int i = 0; i < container.getContainerSize(); i++) {
                    ItemStack stack = container.getItem(i);
                    if (stack.isEmpty()) continue;
                    slots.add(new SlotView(observed.pos(), container, false, i, null, stack.copy()));
                }
            }
            if (useHandlers) {
                searchContext.forEachUniqueItemHandler(observed.pos(), (side, handler) -> {
                    for (int i = 0; i < handler.getSlots(); i++) {
                        ItemStack stack = handler.getStackInSlot(i);
                        if (stack.isEmpty()) continue;
                        slots.add(new SlotView(observed.pos(), null, true, i, side, stack.copy()));
                    }
                });
            }
            if (StorageRoles.useRank(roles.isEmpty()
                    ? Set.of(StorageRoleDef.Role.STORAGE) : roles, StorageUse.INGREDIENT)
                    != Integer.MAX_VALUE) {
                for (SlotView slot : slots) accumulate(itemCounts, slot.stack());
            }
            if (!slots.isEmpty()) {
                entries.add(new Entry(observed.pos(), List.copyOf(slots), candidate.buildingRank(), roles));
            }
        }

        entries.sort(Comparator.comparingInt(Entry::buildingRank));

        return new Snapshot(List.copyOf(entries), Map.copyOf(itemCounts), gameTime + SNAPSHOT_TTL_TICKS);
    }

    public static List<BlockPos> candidateStoragePositions(ServerLevel level, Set<Long> kitchenBounds) {
        return WorksiteStorageCandidateIndex.candidates(level, kitchenBounds);
    }

    public record Snapshot(List<Entry> entries, Map<ResourceLocation, Integer> itemCounts, long expiresAt) {
        public boolean validAt(long gameTime) {
            return gameTime <= expiresAt;
        }

        boolean containsPosition(long packedPos) {
            for (Entry entry : entries) {
                if (entry.pos().asLong() == packedPos) return true;
            }
            return false;
        }

        public Map<ResourceLocation, Integer> supply(Set<ResourceLocation> trackedIds, ServerLevel level) {
            Map<ResourceLocation, Integer> supply = new HashMap<>();
            if (trackedIds.isEmpty()) return supply;
            for (ResourceLocation trackedId : trackedIds) {
                int count = itemCounts.getOrDefault(trackedId, 0);
                if (count > 0) supply.put(trackedId, count);
            }
            // Supply lines are counted by what a stack is, not by its id, so they are the one
            // thing itemCounts cannot answer. Walk stored slots only for the lines this caller is
            // actually planning against — with none in play (the usual case) there is no walk.
            List<SupplyLines.Line> lines = SupplyLines.activeLinesAmong(trackedIds);
            if (lines.isEmpty()) return supply;
            for (Entry entry : entries) {
                if (roleRank(entry.roles(), StorageUse.INGREDIENT) == Integer.MAX_VALUE) continue;
                for (SlotView slot : entry.slots()) {
                    ItemStack stack = slot.stack();
                    if (stack.isEmpty()) continue;
                    for (SupplyLines.Line line : lines) {
                        if (line.matches(stack, level)) {
                            supply.merge(line.id(), stack.getCount(), Integer::sum);
                        }
                    }
                }
            }
            return supply;
        }

        public @Nullable NearbyItemSources.ContainerSlot findBestSlot(VillagerEntityMCA villager, Predicate<ItemStack> matcher) {
            return findBestSlot(villager, matcher, ItemStack::getCount, StorageUse.INGREDIENT);
        }

        public @Nullable NearbyItemSources.ContainerSlot findBestSlot(
                VillagerEntityMCA villager, Predicate<ItemStack> matcher, ToIntFunction<ItemStack> scorer) {
            return findBestSlot(villager, matcher, scorer, StorageUse.INGREDIENT);
        }

        public @Nullable NearbyItemSources.ContainerSlot findBestSlot(
                VillagerEntityMCA villager, Predicate<ItemStack> matcher, StorageUse use) {
            return findBestSlot(villager, matcher, ItemStack::getCount, use);
        }

        public @Nullable NearbyItemSources.ContainerSlot findBestSlot(
                VillagerEntityMCA villager, Predicate<ItemStack> matcher,
                ToIntFunction<ItemStack> scorer, StorageUse use) {
            NearbyItemSources.ContainerSlot best = null;
            int bestBuildingRank = StoragePreference.FALLBACK_RANK;
            int bestStorageRank = StoragePreference.FALLBACK_RANK;
            for (Entry entry : entries) {
                int storageRank = roleRank(entry.roles(), use);
                if (storageRank == Integer.MAX_VALUE) continue;
                for (SlotView slot : entry.slots()) {
                    if (!matcher.test(slot.stack())) continue;
                    if (entry.buildingRank() > bestBuildingRank
                            || (entry.buildingRank() == bestBuildingRank
                            && storageRank > bestStorageRank)) continue;
                    int score = scorer.applyAsInt(slot.stack());
                    double dist = villager.distanceToSqr(
                            slot.pos().getX() + 0.5,
                            slot.pos().getY() + 0.5,
                            slot.pos().getZ() + 0.5
                    );
                    boolean betterPlace = entry.buildingRank() < bestBuildingRank
                            || (entry.buildingRank() == bestBuildingRank
                            && storageRank < bestStorageRank);
                    if (betterPlace || isBetter(best, dist, score)) {
                        bestBuildingRank = entry.buildingRank();
                        bestStorageRank = storageRank;
                        best = new NearbyItemSources.ContainerSlot(
                                slot.pos(),
                                slot.container(),
                                slot.itemHandler(),
                                slot.slot(),
                                score,
                                dist,
                                slot.side()
                        );
                    }
                }
            }
            return best;
        }

        List<SlotView> matchingSlots(Set<ResourceLocation> itemIds) {
            if (itemIds == null || itemIds.isEmpty()) return List.of();
            List<SlotView> matching = new ArrayList<>();
            for (Entry entry : entries) {
                if (roleRank(entry.roles(), StorageUse.INGREDIENT) == Integer.MAX_VALUE) continue;
                for (SlotView slot : entry.slots()) {
                    ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(slot.stack().getItem());
                    if (itemId == null || !itemIds.contains(itemId)) continue;
                    matching.add(slot);
                }
            }
            return List.copyOf(matching);
        }

        public ExtractionPlan planIngredientExtraction(RecipeIngredient ingredient, int requestedCount) {
            if (ingredient == null || requestedCount <= 0) return new ExtractionPlan(List.of(), 0);
            return planExtraction(Set.copyOf(ingredient.itemIds()), requestedCount);
        }

        public ExtractionPlan planItemExtraction(ResourceLocation itemId, int requestedCount) {
            if (itemId == null || requestedCount <= 0) return new ExtractionPlan(List.of(), 0);
            return planExtraction(Set.of(itemId), requestedCount);
        }

        private ExtractionPlan planExtraction(Set<ResourceLocation> itemIds, int requestedCount) {
            List<PlannedExtraction> planned = new ArrayList<>();
            int remaining = requestedCount;
            int totalAvailable = 0;
            for (SlotView slot : matchingSlots(itemIds)) {
                ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(slot.stack().getItem());
                if (itemId == null) continue;
                int available = slot.stack().getCount();
                if (available <= 0) continue;
                totalAvailable += available;
                if (remaining <= 0) continue;
                int reserved = Math.min(available, remaining);
                planned.add(new PlannedExtraction(toContainerSlot(slot), itemId, reserved));
                remaining -= reserved;
            }
            return new ExtractionPlan(List.copyOf(planned), totalAvailable);
        }
    }

    private record CandidatePosition(BlockPos pos, int buildingRank) {}

    private record Entry(BlockPos pos, List<SlotView> slots, int buildingRank,
                         Set<StorageRoleDef.Role> roles) {}

    record SlotView(BlockPos pos, @Nullable Container container, boolean itemHandler, int slot,
                            @Nullable Direction side, ItemStack stack) {}

    public record PlannedExtraction(NearbyItemSources.ContainerSlot slot, ResourceLocation itemId, int count) {}

    public record ExtractionPlan(List<PlannedExtraction> slots, int totalAvailable) {}

    private record SnapshotKey(String dimensionId, String professionId, java.util.UUID villagerId,
                               BoundsKey boundsKey) {
        static SnapshotKey create(ServerLevel level, VillagerEntityMCA villager, Set<Long> kitchenBounds) {
            ResourceLocation profession = BuiltInRegistries.VILLAGER_PROFESSION
                    .getKey(villager.getVillagerData().getProfession());
            return new SnapshotKey(
                    level.dimension().location().toString(),
                    profession == null ? "minecraft:none" : profession.toString(),
                    villager.getUUID(),
                    BoundsKey.of(kitchenBounds)
            );
        }
    }

    public static record BoundsKey(long[] positions, int cachedHash) {
        public static BoundsKey of(Set<Long> kitchenBounds) {
            long[] positions = new long[kitchenBounds.size()];
            int index = 0;
            for (long pos : kitchenBounds) {
                positions[index++] = pos;
            }
            java.util.Arrays.sort(positions);
            return new BoundsKey(positions, java.util.Arrays.hashCode(positions));
        }

        @Override
        public int hashCode() {
            return cachedHash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof BoundsKey other)) return false;
            return java.util.Arrays.equals(positions, other.positions);
        }

        public boolean positionsContain(long pos) {
            return java.util.Arrays.binarySearch(positions, pos) >= 0;
        }

        public boolean candidateSearchContains(long pos) {
            BlockPos changedPos = BlockPos.of(pos);
            int changedX = changedPos.getX();
            int changedY = changedPos.getY();
            int changedZ = changedPos.getZ();
            for (long boundKey : positions) {
                BlockPos base = BlockPos.of(boundKey);
                if (Math.abs(changedX - base.getX()) <= 2
                        && Math.abs(changedY - base.getY()) <= 1
                        && Math.abs(changedZ - base.getZ()) <= 2) {
                    return true;
                }
            }
            return false;
        }
    }

    private static boolean isBetter(@Nullable NearbyItemSources.ContainerSlot currentBest, double candidateDist, int candidateScore) {
        if (currentBest == null) return true;
        if (candidateDist < currentBest.distanceSqr() - 4.0d) return true;
        return candidateDist < currentBest.distanceSqr() + 4.0d && candidateScore > currentBest.score();
    }

    private static void accumulate(Map<ResourceLocation, Integer> itemCounts, ItemStack stack) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (itemId == null) return;
        itemCounts.merge(itemId, stack.getCount(), Integer::sum);
    }

    private static int roleRank(Set<StorageRoleDef.Role> roles, StorageUse use) {
        return StorageRoles.useRank(roles == null || roles.isEmpty()
                ? Set.of(StorageRoleDef.Role.STORAGE) : roles, use);
    }

    private static NearbyItemSources.ContainerSlot toContainerSlot(SlotView slot) {
        int score = slot.stack().getCount();
        return new NearbyItemSources.ContainerSlot(
                slot.pos(),
                slot.container(),
                slot.itemHandler(),
                slot.slot(),
                score,
                0.0d,
                slot.side()
        );
    }

}
