package com.aetherianartificer.townstead.hunger;

import com.aetherianartificer.townstead.storage.StorageSearchContext;
import com.aetherianartificer.townstead.storage.VillageAiBudget;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
//? if >=1.21 {
import net.minecraft.core.component.DataComponents;
//?}
import net.minecraft.world.Container;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

final class NearbyStorageIndex {
    private static final long SNAPSHOT_TTL_TICKS = 10L;
    private static final int REFRESH_BUDGET_PER_TICK = 4;
    private static final Map<SnapshotKey, Snapshot> SNAPSHOTS = new ConcurrentHashMap<>();

    private NearbyStorageIndex() {}

    static Snapshot snapshot(ServerLevel level, BlockPos center, int horizontalRadius, int verticalRadius) {
        SnapshotKey key = new SnapshotKey(level.dimension().location().toString(), center.asLong(), horizontalRadius, verticalRadius);
        Snapshot current = SNAPSHOTS.get(key);
        long gameTime = level.getGameTime();
        if (current != null && current.validAt(gameTime)) {
            return current;
        }
        if (current != null && !VillageAiBudget.tryConsume(level, "nearby-storage:" + key.centerKey() + ":" + horizontalRadius + ":" + verticalRadius, REFRESH_BUDGET_PER_TICK)) {
            return current;
        }
        Snapshot rebuilt = buildSnapshot(level, center, horizontalRadius, verticalRadius, gameTime);
        SNAPSHOTS.put(key, rebuilt);
        return rebuilt;
    }

    static void invalidate(ServerLevel level) {
        String dimensionId = level.dimension().location().toString();
        SNAPSHOTS.keySet().removeIf(key -> key.dimensionId().equals(dimensionId));
    }

    static void invalidate(ServerLevel level, BlockPos changedPos) {
        if (level == null || changedPos == null) return;
        String dimensionId = level.dimension().location().toString();
        for (Map.Entry<SnapshotKey, Snapshot> entry : SNAPSHOTS.entrySet()) {
            SnapshotKey key = entry.getKey();
            if (!key.dimensionId().equals(dimensionId) || !contains(key, changedPos)) continue;
            SNAPSHOTS.put(key, refreshSnapshotEntry(level, entry.getValue(), changedPos));
        }
    }

    private static Snapshot buildSnapshot(ServerLevel level, BlockPos center, int horizontalRadius, int verticalRadius, long gameTime) {
        int observedWidth = horizontalRadius * 2 + 1;
        int observedHeight = verticalRadius * 2 + 1;
        int expectedObservedBlocks = observedWidth * observedHeight * observedWidth;
        StorageSearchContext searchContext = new StorageSearchContext(level, expectedObservedBlocks, expectedObservedBlocks / 2);
        List<Entry> entries = new ArrayList<>();

        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-horizontalRadius, -verticalRadius, -horizontalRadius),
                center.offset(horizontalRadius, verticalRadius, horizontalRadius))) {

            BlockState state = searchContext.getState(pos);
            if (!state.hasBlockEntity()) continue;
            BlockEntity blockEntity = searchContext.getBlockEntity(pos);
            if (blockEntity == null) continue;
            if (NearbyItemSources.isProcessingContainer(state, blockEntity)) continue;
            if (searchContext.isProtectedStorage(pos, state)) continue;
            BlockPos immutablePos = pos.immutable();

            List<SlotView> containerSlots = new ArrayList<>();
            List<SlotView> allSlots = new ArrayList<>();

            if (blockEntity instanceof Container container) {
                for (int i = 0; i < container.getContainerSize(); i++) {
                    ItemStack stack = container.getItem(i);
                    if (stack.isEmpty()) continue;
                    ItemStack copy = stack.copy();
                    SlotView slotView = new SlotView(immutablePos, container, false, i, null, copy);
                    containerSlots.add(slotView);
                    allSlots.add(slotView);
                }
            }

            searchContext.forEachUniqueItemHandler(immutablePos, (side, handler) -> {
                for (int i = 0; i < handler.getSlots(); i++) {
                    ItemStack stack = handler.getStackInSlot(i);
                    if (stack.isEmpty()) continue;
                    allSlots.add(new SlotView(immutablePos, null, true, i, side, stack.copy()));
                }
            });

            if (!containerSlots.isEmpty() || !allSlots.isEmpty()) {
                entries.add(new Entry(
                        immutablePos,
                        List.copyOf(containerSlots),
                        List.copyOf(allSlots),
                        bestFoodSlot(immutablePos, containerSlots)
                ));
            }
        }

        return new Snapshot(List.copyOf(entries), gameTime + SNAPSHOT_TTL_TICKS);
    }

    record Snapshot(List<Entry> entries, long expiresAt) {
        boolean validAt(long gameTime) {
            return gameTime <= expiresAt;
        }

        @Nullable NearbyItemSources.ContainerSlot findBestNearbySlot(VillagerEntityMCA villager,
                                                                     BlockPos center,
                                                                     int horizontalRadius,
                                                                     int verticalRadius,
                                                                     Predicate<ItemStack> matcher,
                                                                     ToIntFunction<ItemStack> scorer) {
            NearbyItemSources.ContainerSlot best = null;
            for (Entry entry : entries) {
                for (SlotView slot : entry.allSlots()) {
                    if (!matcher.test(slot.stack())) continue;
                    int score = scorer.applyAsInt(slot.stack());
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

        void collectMatchingContainerSlots(VillagerEntityMCA villager,
                                           BlockPos center,
                                           int horizontalRadius,
                                           int verticalRadius,
                                           Predicate<ItemStack> matcher,
                                           ToIntFunction<ItemStack> scorer,
                                           Consumer<NearbyItemSources.ContainerSlot> consumer) {
            for (Entry entry : entries) {
                NearbyItemSources.ContainerSlot bestInContainer = null;
                for (SlotView slot : entry.containerSlots()) {
                    if (!matcher.test(slot.stack())) continue;
                    int score = scorer.applyAsInt(slot.stack());
                    double dist = villager.distanceToSqr(
                            slot.pos().getX() + 0.5,
                            slot.pos().getY() + 0.5,
                            slot.pos().getZ() + 0.5
                    );
                    if (bestInContainer == null || score > bestInContainer.score()) {
                        bestInContainer = new NearbyItemSources.ContainerSlot(
                                slot.pos(),
                                slot.container(),
                                false,
                                slot.slot(),
                                score,
                                dist,
                                null
                        );
                    }
                }
                if (bestInContainer != null) {
                    consumer.accept(bestInContainer);
                }
            }
        }

        void collectBestFoodContainerSlots(VillagerEntityMCA villager,
                                           BlockPos center,
                                           int horizontalRadius,
                                           int verticalRadius,
                                           Consumer<NearbyItemSources.ContainerSlot> consumer) {
            for (Entry entry : entries) {
                FoodSlotView bestFoodSlot = entry.bestFoodSlot();
                if (bestFoodSlot == null) continue;
                double dist = villager.distanceToSqr(
                        bestFoodSlot.pos().getX() + 0.5,
                        bestFoodSlot.pos().getY() + 0.5,
                        bestFoodSlot.pos().getZ() + 0.5
                );
                consumer.accept(new NearbyItemSources.ContainerSlot(
                        bestFoodSlot.pos(),
                        bestFoodSlot.container(),
                        false,
                        bestFoodSlot.slot(),
                        bestFoodSlot.nutrition(),
                        dist,
                        null
                ));
            }
        }

        @Nullable NearbyItemSources.ContainerSlot findBestDrinkNearbySlot(VillagerEntityMCA villager,
                                                                          BlockPos center,
                                                                          int horizontalRadius,
                                                                          int verticalRadius,
                                                                          ToIntFunction<ItemStack> scorer) {
            NearbyItemSources.ContainerSlot best = null;
            for (Entry entry : entries) {
                for (SlotView slot : entry.allSlots()) {
                    int score = scorer.applyAsInt(slot.stack());
                    if (score <= 0) continue;
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

    private record Entry(BlockPos pos, List<SlotView> containerSlots, List<SlotView> allSlots,
                         @Nullable FoodSlotView bestFoodSlot) {}

    private record SlotView(BlockPos pos, @Nullable Container container, boolean itemHandler, int slot,
                            @Nullable Direction side, ItemStack stack) {}

    private record FoodSlotView(BlockPos pos, Container container, int slot, int nutrition) {}

    private record SnapshotKey(String dimensionId, long centerKey, int horizontalRadius, int verticalRadius) {}

    private static boolean contains(SnapshotKey key, BlockPos pos) {
        BlockPos center = BlockPos.of(key.centerKey());
        return Math.abs(pos.getX() - center.getX()) <= key.horizontalRadius()
                && Math.abs(pos.getY() - center.getY()) <= key.verticalRadius()
                && Math.abs(pos.getZ() - center.getZ()) <= key.horizontalRadius();
    }

    private static Snapshot refreshSnapshotEntry(ServerLevel level, Snapshot snapshot, BlockPos changedPos) {
        List<Entry> refreshed = new ArrayList<>();
        for (Entry entry : snapshot.entries()) {
            if (!entry.pos().equals(changedPos)) {
                refreshed.add(entry);
            }
        }
        Entry rescanned = scanEntry(level, changedPos);
        if (rescanned != null) {
            refreshed.add(rescanned);
        }
        return new Snapshot(List.copyOf(refreshed), snapshot.expiresAt());
    }

    private static @Nullable Entry scanEntry(ServerLevel level, BlockPos pos) {
        StorageSearchContext searchContext = new StorageSearchContext(level);
        BlockState state = searchContext.getState(pos);
        if (!state.hasBlockEntity()) return null;
        BlockEntity blockEntity = searchContext.getBlockEntity(pos);
        if (blockEntity == null) return null;
        if (NearbyItemSources.isProcessingContainer(state, blockEntity)) return null;
        if (searchContext.isProtectedStorage(pos, state)) return null;
        BlockPos immutablePos = pos.immutable();

        List<SlotView> containerSlots = new ArrayList<>();
        List<SlotView> allSlots = new ArrayList<>();

        if (blockEntity instanceof Container container) {
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack stack = container.getItem(i);
                if (stack.isEmpty()) continue;
                ItemStack copy = stack.copy();
                SlotView slotView = new SlotView(immutablePos, container, false, i, null, copy);
                containerSlots.add(slotView);
                allSlots.add(slotView);
            }
        }

        searchContext.forEachUniqueItemHandler(immutablePos, (side, handler) -> {
            for (int i = 0; i < handler.getSlots(); i++) {
                ItemStack stack = handler.getStackInSlot(i);
                if (stack.isEmpty()) continue;
                allSlots.add(new SlotView(immutablePos, null, true, i, side, stack.copy()));
            }
        });

        if (containerSlots.isEmpty() && allSlots.isEmpty()) {
            return null;
        }
        return new Entry(
                immutablePos,
                List.copyOf(containerSlots),
                List.copyOf(allSlots),
                bestFoodSlot(immutablePos, containerSlots)
        );
    }

    private static boolean isBetter(@Nullable NearbyItemSources.ContainerSlot currentBest, double candidateDist, int candidateScore) {
        if (currentBest == null) return true;
        if (candidateDist < currentBest.distanceSqr() - 4.0d) return true;
        return candidateDist < currentBest.distanceSqr() + 4.0d && candidateScore > currentBest.score();
    }

    private static @Nullable FoodSlotView bestFoodSlot(BlockPos pos, List<SlotView> containerSlots) {
        FoodSlotView best = null;
        for (SlotView slotView : containerSlots) {
            int nutrition = foodNutrition(slotView.stack());
            if (nutrition <= 0 || slotView.container() == null) continue;
            if (best == null || nutrition > best.nutrition()) {
                best = new FoodSlotView(pos, slotView.container(), slotView.slot(), nutrition);
            }
        }
        return best;
    }

    private static int foodNutrition(ItemStack stack) {
        //? if >=1.21 {
        FoodProperties food = stack.get(DataComponents.FOOD);
        return food != null ? food.nutrition() : 0;
        //?} else {
        /*FoodProperties food = stack.getFoodProperties(null);
        return food != null ? food.getNutrition() : 0;
        *///?}
    }
}
