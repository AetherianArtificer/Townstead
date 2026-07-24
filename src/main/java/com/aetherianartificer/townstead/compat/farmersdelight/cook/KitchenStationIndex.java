package com.aetherianartificer.townstead.compat.farmersdelight.cook;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import com.aetherianartificer.townstead.storage.VillageAiBudget;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class KitchenStationIndex {
    private static final long SNAPSHOT_TTL_TICKS = 20L;
    private static final int REFRESH_BUDGET_PER_TICK = 2;
    private static final Map<SnapshotKey, Snapshot> SNAPSHOTS = new ConcurrentHashMap<>();

    private KitchenStationIndex() {}

    static Snapshot snapshot(ServerLevel level, Set<Long> kitchenBounds) {
        SnapshotKey key = SnapshotKey.create(level, kitchenBounds);
        Snapshot current = SNAPSHOTS.get(key);
        long gameTime = level.getGameTime();
        if (current != null && current.validAt(gameTime)) {
            return current;
        }
        if (current != null && !VillageAiBudget.tryConsume(level, "kitchen-station:" + key.boundsKey().cachedHash(), REFRESH_BUDGET_PER_TICK)) {
            return current;
        }
        Snapshot rebuilt = buildSnapshot(level, kitchenBounds, gameTime);
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
        long changedKey = changedPos.asLong();
        for (Map.Entry<SnapshotKey, Snapshot> entry : SNAPSHOTS.entrySet()) {
            SnapshotKey key = entry.getKey();
            if (!key.dimensionId().equals(dimensionId) || !key.boundsKey().positionsContain(changedKey)) continue;
            SNAPSHOTS.put(key, refreshSnapshotEntry(level, entry.getValue(), changedPos));
        }
    }

    private static Snapshot buildSnapshot(ServerLevel level, Set<Long> kitchenBounds, long gameTime) {
        List<StationHandler.StationSlot> stations = new ArrayList<>();
        java.util.Set<Long> claimed = new java.util.HashSet<>();
        for (long key : kitchenBounds) {
            BlockPos pos = BlockPos.of(key);
            ModRecipeRegistry.StationType type = StationHandler.stationType(level, pos);
            if (type == null) continue;
            ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
            int capacity = stationCapacity(level, pos, type);
            if (capacity <= 0) continue;
            claimed.add(pos.asLong());
            stations.add(new StationHandler.StationSlot(pos.immutable(), type, blockId, capacity));
        }
        // Empty placement anchors: the free cell above a declared place-surface (a stove top
        // with nothing on it) is a station a villager can create by placing the work block.
        for (long key : kitchenBounds) {
            BlockPos surface = BlockPos.of(key);
            BlockPos above = surface.above();
            if (claimed.contains(above.asLong()) || !level.getBlockState(above).isAir()) continue;
            WorkstationDef def = StationProtocols.surfaceDefBelow(level, above);
            if (def == null) continue;
            claimed.add(above.asLong());
            stations.add(new StationHandler.StationSlot(above.immutable(),
                    ModRecipeRegistry.StationType.PLACE_SURFACE,
                    BuiltInRegistries.BLOCK.getKey(level.getBlockState(surface).getBlock()), 1));
        }
        return new Snapshot(List.copyOf(stations), gameTime + SNAPSHOT_TTL_TICKS);
    }

    private static int stationCapacity(ServerLevel level, BlockPos pos, ModRecipeRegistry.StationType type) {
        return switch (type) {
            case FIRE_STATION -> StationHandler.surfaceFreeSlotCount(level, pos);
            case HOT_STATION, CUTTING_BOARD, PASSIVE_STATION, PLACE_SURFACE -> 1;
        };
    }

    record Snapshot(List<StationHandler.StationSlot> stations, long expiresAt) {
        boolean validAt(long gameTime) {
            return gameTime <= expiresAt;
        }
    }

    private record SnapshotKey(String dimensionId, KitchenStorageIndex.BoundsKey boundsKey) {
        static SnapshotKey create(ServerLevel level, Set<Long> kitchenBounds) {
            return new SnapshotKey(level.dimension().location().toString(), KitchenStorageIndex.BoundsKey.of(kitchenBounds));
        }
    }

    private static Snapshot refreshSnapshotEntry(ServerLevel level, Snapshot snapshot, BlockPos changedPos) {
        List<StationHandler.StationSlot> refreshed = new ArrayList<>();
        for (StationHandler.StationSlot station : snapshot.stations()) {
            if (!station.pos().equals(changedPos)) {
                refreshed.add(station);
            }
        }
        ModRecipeRegistry.StationType type = StationHandler.stationType(level, changedPos);
        if (type != null) {
            ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(changedPos).getBlock());
            int capacity = stationCapacity(level, changedPos, type);
            if (capacity > 0) {
                refreshed.add(new StationHandler.StationSlot(changedPos.immutable(), type, blockId, capacity));
            }
        }
        return new Snapshot(List.copyOf(refreshed), snapshot.expiresAt());
    }
}
