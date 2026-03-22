package com.aetherianartificer.townstead.compat.farmersdelight.cook;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.compat.farmersdelight.FarmersDelightCookAssignment;
import com.aetherianartificer.townstead.compat.thirst.ThirstCompatBridge;
import com.aetherianartificer.townstead.hunger.NearbyItemSources;
import com.aetherianartificer.townstead.storage.VillageAiBudget;
import com.aetherianartificer.townstead.storage.VillageStorageIndex;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

final class KitchenStorageIndex {
    private static final long SNAPSHOT_TTL_TICKS = 20L;
    private static final int REFRESH_BUDGET_PER_TICK = 2;
    private static final Map<SnapshotKey, Snapshot> SNAPSHOTS = new ConcurrentHashMap<>();

    private KitchenStorageIndex() {}

    static Snapshot snapshot(ServerLevel level, VillagerEntityMCA villager, Set<Long> kitchenBounds) {
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
        VillageStorageIndex.invalidate(level);
    }

    static void invalidate(ServerLevel level, BlockPos changedPos) {
        if (level == null || changedPos == null) return;
        VillageStorageIndex.invalidate(level, changedPos);
        String dimensionId = level.dimension().location().toString();
        long changedKey = changedPos.asLong();
        SNAPSHOTS.keySet().removeIf(key -> key.dimensionId.equals(dimensionId)
                && key.boundsKey.positionsContain(changedKey));
    }

    private static Snapshot buildSnapshot(ServerLevel level, VillagerEntityMCA villager, Set<Long> kitchenBounds, long gameTime) {
        Optional<Village> villageOpt = FarmersDelightCookAssignment.resolveVillage(villager);
        if (villageOpt.isEmpty() || kitchenBounds.isEmpty()) {
            return new Snapshot(List.of(), Map.of(), gameTime + SNAPSHOT_TTL_TICKS);
        }

        Village village = villageOpt.get();
        VillageStorageIndex.Snapshot villageSnapshot = VillageStorageIndex.snapshot(level, village);
        List<Entry> entries = new ArrayList<>();
        Map<ResourceLocation, Integer> itemCounts = new HashMap<>();

        for (VillageStorageIndex.Entry villageEntry : villageSnapshot.entries()) {
            BlockPos pos = villageEntry.pos();
            if (!StationHandler.isInKitchenWorkArea(kitchenBounds, pos)) continue;

            List<SlotView> slots = new ArrayList<>();
            for (VillageStorageIndex.SlotView villageSlot : villageEntry.slots()) {
                ItemStack copy = villageSlot.stack().copy();
                slots.add(new SlotView(pos, villageSlot.container(), villageSlot.itemHandler(), villageSlot.slot(), villageSlot.side(), copy));
                accumulate(itemCounts, copy);
            }
            if (!slots.isEmpty()) {
                entries.add(new Entry(pos, List.copyOf(slots)));
            }
        }

        return new Snapshot(List.copyOf(entries), Map.copyOf(itemCounts), gameTime + SNAPSHOT_TTL_TICKS);
    }

    record Snapshot(List<Entry> entries, Map<ResourceLocation, Integer> itemCounts, long expiresAt) {
        boolean validAt(long gameTime) {
            return gameTime <= expiresAt;
        }

        Map<ResourceLocation, Integer> supply(Set<ResourceLocation> trackedIds,
                                              boolean trackImpureWater,
                                              @Nullable ThirstCompatBridge thirstBridge) {
            Map<ResourceLocation, Integer> supply = new HashMap<>();
            if (trackedIds.isEmpty() && !trackImpureWater) return supply;
            for (ResourceLocation trackedId : trackedIds) {
                int count = itemCounts.getOrDefault(trackedId, 0);
                if (count > 0) supply.put(trackedId, count);
            }
            for (Entry entry : entries) {
                for (SlotView slot : entry.slots()) {
                    ItemStack stack = slot.stack();
                    if (stack.isEmpty()) continue;
                    if (trackImpureWater && thirstBridge != null
                            && StationHandler.impureWaterScore(stack, thirstBridge) > 0) {
                        supply.merge(TownsteadKitchenConstants.TOWNSTEAD_IMPURE_WATER_INPUT, stack.getCount(), Integer::sum);
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
    }

    private record Entry(BlockPos pos, List<SlotView> slots) {}

    private record SlotView(BlockPos pos, @Nullable Container container, boolean itemHandler, int slot,
                            @Nullable Direction side, ItemStack stack) {}

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

    static final class TownsteadKitchenConstants {
        //? if >=1.21 {
        static final ResourceLocation TOWNSTEAD_IMPURE_WATER_INPUT =
                ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "impure_water_container");
        //?} else {
        /*static final ResourceLocation TOWNSTEAD_IMPURE_WATER_INPUT =
                new ResourceLocation(Townstead.MOD_ID, "impure_water_container");
        *///?}

        private TownsteadKitchenConstants() {}
    }
}
