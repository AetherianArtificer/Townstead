package com.aetherianartificer.townstead.storage;

import com.aetherianartificer.townstead.hunger.NearbyItemSources;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared village-scoped storage discovery. Higher-level indexes can filter this
 * snapshot for kitchen, cafe, or other work-area-specific views without
 * repeating the base village storage scan.
 */
public final class VillageStorageIndex {
    private static final long SNAPSHOT_TTL_TICKS = 20L;
    private static final int REFRESH_BUDGET_PER_TICK = 2;
    private static final Map<SnapshotKey, Snapshot> SNAPSHOTS = new ConcurrentHashMap<>();

    private VillageStorageIndex() {}

    public static Snapshot snapshot(ServerLevel level, Village village) {
        if (level == null || village == null) {
            return new Snapshot(List.of(), 0L);
        }
        SnapshotKey key = new SnapshotKey(level.dimension().location().toString(), village.getId());
        Snapshot current = SNAPSHOTS.get(key);
        long gameTime = level.getGameTime();
        if (current != null && current.validAt(gameTime)) {
            return current;
        }
        if (current != null && !VillageAiBudget.tryConsume(level, "village-storage:" + village.getId(), REFRESH_BUDGET_PER_TICK)) {
            return current;
        }
        Snapshot rebuilt = buildSnapshot(level, village, gameTime);
        SNAPSHOTS.put(key, rebuilt);
        return rebuilt;
    }

    public static void invalidate(ServerLevel level, Village village) {
        if (level == null || village == null) return;
        SNAPSHOTS.remove(new SnapshotKey(level.dimension().location().toString(), village.getId()));
    }

    public static void invalidate(ServerLevel level) {
        if (level == null) return;
        String dimensionId = level.dimension().location().toString();
        SNAPSHOTS.keySet().removeIf(key -> key.dimensionId().equals(dimensionId));
    }

    public static void invalidate(ServerLevel level, BlockPos changedPos) {
        if (level == null || changedPos == null) return;
        String dimensionId = level.dimension().location().toString();
        long changedKey = changedPos.asLong();
        SNAPSHOTS.entrySet().removeIf(entry -> entry.getKey().dimensionId().equals(dimensionId)
                && entry.getValue().contains(changedKey));
    }

    private static Snapshot buildSnapshot(ServerLevel level, Village village, long gameTime) {
        StorageSearchContext searchContext = new StorageSearchContext(level);
        List<Entry> entries = new ArrayList<>();
        Set<Long> visited = new HashSet<>();

        for (Building building : village.getBuildings().values()) {
            for (BlockPos pos : (Iterable<BlockPos>) building.getBlockPosStream()::iterator) {
                long posKey = pos.asLong();
                if (!visited.add(posKey)) continue;
                if (!village.isWithinBorder(pos, 0)) continue;

                StorageSearchContext.ObservedBlock observed = searchContext.observe(pos);
                if (observed.protectedStorage()) continue;
                BlockEntity be = observed.blockEntity();
                if (be == null) continue;
                if (NearbyItemSources.isProcessingContainer(observed.state(), be)) continue;

                List<SlotView> slots = new ArrayList<>();
                if (be instanceof Container container) {
                    for (int i = 0; i < container.getContainerSize(); i++) {
                        ItemStack stack = container.getItem(i);
                        if (stack.isEmpty()) continue;
                        slots.add(new SlotView(observed.pos(), container, false, i, null, stack.copy()));
                    }
                }

                searchContext.forEachUniqueItemHandler(observed.pos(), (side, handler) -> {
                    for (int i = 0; i < handler.getSlots(); i++) {
                        ItemStack stack = handler.getStackInSlot(i);
                        if (stack.isEmpty()) continue;
                        slots.add(new SlotView(observed.pos(), null, true, i, side, stack.copy()));
                    }
                });

                if (!slots.isEmpty()) {
                    entries.add(new Entry(observed.pos(), List.copyOf(slots)));
                }
            }
        }

        return new Snapshot(List.copyOf(entries), gameTime + SNAPSHOT_TTL_TICKS);
    }

    public record Snapshot(List<Entry> entries, long expiresAt) {
        boolean validAt(long gameTime) {
            return gameTime <= expiresAt;
        }

        boolean contains(long posKey) {
            for (Entry entry : entries) {
                if (entry.pos().asLong() == posKey) return true;
            }
            return false;
        }
    }

    public record Entry(BlockPos pos, List<SlotView> slots) {}

    public record SlotView(BlockPos pos, @Nullable Container container, boolean itemHandler, int slot,
                           @Nullable Direction side, ItemStack stack) {}

    private record SnapshotKey(String dimensionId, int villageId) {}
}
