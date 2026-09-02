package com.aetherianartificer.townstead.storage;

import com.aetherianartificer.townstead.work.station.StationInventoryOps;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Chooses one real storage block and transfers a worker's carried goods only while the worker is
 * standing beside it. This class deliberately does not navigate: a work behavior owns its route
 * and calls {@link #depositMatchingAt} after arrival.
 */
public final class PhysicalStorageDelivery {
    private PhysicalStorageDelivery() {}

    /**
     * Finds the best non-excluded storage that can currently accept at least one matching stack.
     * Named profession storage buildings win, followed by preferred block kinds, then ordinary
     * storage beside the worksite. Distance only breaks ties within the same preference rank.
     */
    public static @Nullable BlockPos findDestination(
            ServerLevel level,
            VillagerEntityMCA villager,
            Set<Long> worksiteBounds,
            Predicate<ItemStack> carried,
            Set<Long> excluded
    ) {
        return findDestination(level, villager, worksiteBounds, carried, excluded, StorageUse.OUTPUT);
    }

    public static @Nullable BlockPos findDestination(
            ServerLevel level,
            VillagerEntityMCA villager,
            Set<Long> worksiteBounds,
            Predicate<ItemStack> carried,
            Set<Long> excluded,
            StorageUse use
    ) {
        if (level == null || villager == null || carried == null || !hasMatching(villager, carried)) {
            return null;
        }
        StoragePreference preference = StoragePreference.forVillager(villager);
        Map<Long, Candidate> unique = new HashMap<>();

        for (BlockPos pos : WorksiteStorageIndex.candidateStoragePositions(level, worksiteBounds)) {
            addCandidate(unique, level, pos, StoragePreference.LOCAL_RANK, villager, use);
        }
        for (var building : PreferredStorageBuildings.resolve(level, villager)) {
            int buildingRank = preference.buildingRank(building.getType());
            building.getBlockPosStream().forEach(pos -> addCandidate(
                    unique, level, pos, buildingRank, villager, use));
        }

        List<Candidate> ordered = new ArrayList<>(unique.values());
        ordered.sort(Comparator.comparingInt(Candidate::buildingRank)
                .thenComparingInt(Candidate::storageRank)
                .thenComparingDouble(Candidate::distanceSqr));

        StorageSearchContext search = new StorageSearchContext(level);
        SimpleContainer inventory = villager.getInventory();
        for (Candidate candidate : ordered) {
            BlockPos pos = candidate.pos();
            if (excluded != null && excluded.contains(pos.asLong())) continue;
            StorageSearchContext.ObservedBlock observed = search.observe(pos);
            if (!StorageRoles.isStorageCandidate(level, pos, observed.blockEntity(), villager, use)) continue;
            if (canAcceptAny(level, search, observed, inventory, carried, use)) return pos;
        }
        return null;
    }

    /** Moves matching inventory stacks into exactly {@code destination}; never searches remotely. */
    public static int depositMatchingAt(
            ServerLevel level,
            VillagerEntityMCA villager,
            BlockPos destination,
            Predicate<ItemStack> carried
    ) {
        return depositMatchingAt(level, villager, destination, carried, StorageUse.OUTPUT);
    }

    public static int depositMatchingAt(
            ServerLevel level,
            VillagerEntityMCA villager,
            BlockPos destination,
            Predicate<ItemStack> carried,
            StorageUse use
    ) {
        if (level == null || villager == null || destination == null || carried == null) return 0;
        StorageSearchContext search = new StorageSearchContext(level);
        StorageSearchContext.ObservedBlock observed = search.observe(destination);
        if (!StorageRoles.isStorageCandidate(level, destination, observed.blockEntity(), villager, use)) return 0;

        SimpleContainer inventory = villager.getInventory();
        int moved = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || !carried.test(stack)) continue;
            if (!StorageRoles.acceptsItem(level, destination, stack, use)) continue;
            int before = stack.getCount();
            insertAt(search, observed, stack, false);
            int inserted = before - stack.getCount();
            if (inserted <= 0) continue;
            moved += inserted;
            if (stack.isEmpty()) inventory.setItem(slot, ItemStack.EMPTY);
        }
        if (moved > 0) {
            inventory.setChanged();
            WorksiteStorageIndex.invalidate(level, destination);
        }
        return moved;
    }

    public static boolean hasMatching(VillagerEntityMCA villager, Predicate<ItemStack> carried) {
        if (villager == null || carried == null) return false;
        SimpleContainer inventory = villager.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && carried.test(stack)) return true;
        }
        return false;
    }

    private static void addCandidate(Map<Long, Candidate> candidates, ServerLevel level, BlockPos pos,
                                     int buildingRank, VillagerEntityMCA villager, StorageUse use) {
        if (pos == null) return;
        BlockPos immutable = pos.immutable();
        int storageRank = StorageRoles.useRank(level.getBlockState(immutable), use);
        if (storageRank == Integer.MAX_VALUE) return;
        double distance = villager.distanceToSqr(
                immutable.getX() + 0.5, immutable.getY() + 0.5, immutable.getZ() + 0.5);
        Candidate candidate = new Candidate(immutable, buildingRank, storageRank, distance);
        candidates.merge(immutable.asLong(), candidate, PhysicalStorageDelivery::betterCandidate);
    }

    private static Candidate betterCandidate(Candidate first, Candidate second) {
        if (second.buildingRank() != first.buildingRank()) {
            return second.buildingRank() < first.buildingRank() ? second : first;
        }
        if (second.storageRank() != first.storageRank()) {
            return second.storageRank() < first.storageRank() ? second : first;
        }
        return second.distanceSqr() < first.distanceSqr() ? second : first;
    }

    private static boolean canAcceptAny(ServerLevel level,
                                        StorageSearchContext search,
                                        StorageSearchContext.ObservedBlock observed,
                                        SimpleContainer inventory,
                                        Predicate<ItemStack> carried,
                                        StorageUse use) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || !carried.test(stack)) continue;
            if (!StorageRoles.acceptsItem(level, observed.pos(), stack, use)) continue;
            ItemStack probe = stack.copy();
            insertAt(search, observed, probe, true);
            if (probe.getCount() < stack.getCount()) return true;
        }
        return false;
    }

    private static void insertAt(StorageSearchContext search,
                                 StorageSearchContext.ObservedBlock observed,
                                 ItemStack stack,
                                 boolean simulate) {
        BlockEntity blockEntity = observed.blockEntity();
        if (blockEntity instanceof Container container) {
            insertIntoContainer(container, stack, simulate);
            // A Container's item-handler capability commonly wraps these same slots. Do not
            // traverse both views and insert twice into one physical inventory.
            return;
        }
        search.forEachUniqueItemHandler(observed.pos(), (side, handler) -> {
            if (stack.isEmpty()) return;
            ItemStack remainder = StationInventoryOps.insert(handler, stack, simulate);
            int accepted = stack.getCount() - remainder.getCount();
            if (accepted > 0) stack.shrink(accepted);
        });
    }

    private static void insertIntoContainer(Container container, ItemStack stack, boolean simulate) {
        for (int slot = 0; slot < container.getContainerSize() && !stack.isEmpty(); slot++) {
            if (!container.canPlaceItem(slot, stack)) continue;
            ItemStack present = container.getItem(slot);
            if (!present.isEmpty() && !StationInventoryOps.sameItemAndComponents(present, stack)) continue;
            int limit = Math.min(container.getMaxStackSize(), stack.getMaxStackSize());
            int room = present.isEmpty() ? limit : limit - present.getCount();
            if (room <= 0) continue;
            int moved = Math.min(room, stack.getCount());
            if (!simulate) {
                if (present.isEmpty()) {
                    container.setItem(slot, StationInventoryOps.copyWithCount(stack, moved));
                } else {
                    present.grow(moved);
                }
                container.setChanged();
            }
            stack.shrink(moved);
        }
    }

    private record Candidate(BlockPos pos, int buildingRank, int storageRank, double distanceSqr) {}
}
