package com.aetherianartificer.townstead.compat.farmersdelight.cook;

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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

public final class KitchenStorageIndex {
    private static final long SNAPSHOT_TTL_TICKS = 20L;
    private static final int REFRESH_BUDGET_PER_TICK = 2;
    private static final Map<SnapshotKey, Snapshot> SNAPSHOTS = new ConcurrentHashMap<>();

    private KitchenStorageIndex() {}

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

    static void invalidate(ServerLevel level) {
        String dimensionId = level.dimension().location().toString();
        SNAPSHOTS.keySet().removeIf(key -> key.dimensionId.equals(dimensionId));
        KitchenStorageCandidateIndex.invalidate(level);
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

    static void invalidate(ServerLevel level, BlockPos changedPos) {
        if (level == null || changedPos == null) return;
        VillageStorageIndex.invalidate(level, changedPos);
        KitchenStorageCandidateIndex.invalidate(level, changedPos);
        String dimensionId = level.dimension().location().toString();
        long changedKey = changedPos.asLong();
        SNAPSHOTS.keySet().removeIf(key -> key.dimensionId.equals(dimensionId)
                && key.boundsKey.candidateSearchContains(changedKey));
    }

    private static Snapshot buildSnapshot(ServerLevel level, VillagerEntityMCA villager, Set<Long> kitchenBounds, long gameTime) {
        if (kitchenBounds.isEmpty()) {
            return new Snapshot(List.of(), Map.of(), gameTime + SNAPSHOT_TTL_TICKS);
        }
        StorageSearchContext searchContext = new StorageSearchContext(level);
        List<Entry> entries = new ArrayList<>();
        Map<ResourceLocation, Integer> itemCounts = new HashMap<>();
        Set<Long> visited = new HashSet<>();

        for (BlockPos pos : candidateStoragePositions(level, kitchenBounds)) {
            if (!visited.add(pos.asLong())) continue;
            StorageSearchContext.ObservedBlock observed = searchContext.observe(pos);
            BlockEntity be = observed.blockEntity();
            if (be == null) continue;
            if (!StationHandler.isCookStorageCandidate(level, observed.pos(), be)) continue;
            List<SlotView> slots = new ArrayList<>();
            if (be instanceof Container container) {
                for (int i = 0; i < container.getContainerSize(); i++) {
                    ItemStack stack = container.getItem(i);
                    if (stack.isEmpty()) continue;
                    slots.add(new SlotView(observed.pos(), container, false, i, null, stack.copy()));
                }
            }
            boolean hasContainerSlots = !slots.isEmpty();
            searchContext.forEachUniqueItemHandler(observed.pos(), (side, handler) -> {
                if (hasContainerSlots) return;
                for (int i = 0; i < handler.getSlots(); i++) {
                    ItemStack stack = handler.getStackInSlot(i);
                    if (stack.isEmpty()) continue;
                    slots.add(new SlotView(observed.pos(), null, true, i, side, stack.copy()));
                }
            });
            for (SlotView slot : slots) {
                accumulate(itemCounts, slot.stack());
            }
            if (!slots.isEmpty()) {
                entries.add(new Entry(observed.pos(), List.copyOf(slots)));
            }
        }

        return new Snapshot(List.copyOf(entries), Map.copyOf(itemCounts), gameTime + SNAPSHOT_TTL_TICKS);
    }

    static List<BlockPos> candidateStoragePositions(ServerLevel level, Set<Long> kitchenBounds) {
        return KitchenStorageCandidateIndex.candidates(level, kitchenBounds);
    }

    public record Snapshot(List<Entry> entries, Map<ResourceLocation, Integer> itemCounts, long expiresAt) {
        boolean validAt(long gameTime) {
            return gameTime <= expiresAt;
        }

        Map<ResourceLocation, Integer> supply(Set<ResourceLocation> trackedIds, ServerLevel level) {
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

        @Nullable NearbyItemSources.ContainerSlot findBestSlot(VillagerEntityMCA villager, Predicate<ItemStack> matcher) {
            NearbyItemSources.ContainerSlot best = null;
            for (Entry entry : entries) {
                for (SlotView slot : entry.slots()) {
                    if (!matcher.test(slot.stack())) continue;
                    int score = slot.stack().getCount();
                    double dist = villager.distanceToSqr(
                            slot.pos().getX() + 0.5,
                            slot.pos().getY() + 0.5,
                            slot.pos().getZ() + 0.5
                    );
                    if (isBetter(best, dist, score)) {
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
                for (SlotView slot : entry.slots()) {
                    ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(slot.stack().getItem());
                    if (itemId == null || !itemIds.contains(itemId)) continue;
                    matching.add(slot);
                }
            }
            return List.copyOf(matching);
        }

        ExtractionPlan planIngredientExtraction(RecipeIngredient ingredient, int requestedCount) {
            if (ingredient == null || requestedCount <= 0) return new ExtractionPlan(List.of(), 0);
            return planExtraction(Set.copyOf(ingredient.itemIds()), requestedCount);
        }

        ExtractionPlan planItemExtraction(ResourceLocation itemId, int requestedCount) {
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

    private record Entry(BlockPos pos, List<SlotView> slots) {}

    record SlotView(BlockPos pos, @Nullable Container container, boolean itemHandler, int slot,
                            @Nullable Direction side, ItemStack stack) {}

    record PlannedExtraction(NearbyItemSources.ContainerSlot slot, ResourceLocation itemId, int count) {}

    record ExtractionPlan(List<PlannedExtraction> slots, int totalAvailable) {}

    private record SnapshotKey(String dimensionId, BoundsKey boundsKey) {
        static SnapshotKey create(ServerLevel level, VillagerEntityMCA villager, Set<Long> kitchenBounds) {
            return new SnapshotKey(
                    level.dimension().location().toString(),
                    BoundsKey.of(kitchenBounds)
            );
        }
    }

    static record BoundsKey(long[] positions, int cachedHash) {
        static BoundsKey of(Set<Long> kitchenBounds) {
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

        boolean positionsContain(long pos) {
            return java.util.Arrays.binarySearch(positions, pos) >= 0;
        }

        boolean candidateSearchContains(long pos) {
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
