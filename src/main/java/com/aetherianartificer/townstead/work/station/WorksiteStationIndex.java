package com.aetherianartificer.townstead.work.station;

import com.aetherianartificer.townstead.storage.WorksiteStorageIndex;

import com.aetherianartificer.townstead.work.station.Stations;

import com.aetherianartificer.townstead.work.station.StationProtocols;

import com.aetherianartificer.townstead.work.recipe.StationType;

import com.aetherianartificer.townstead.work.station.WorkstationDef;

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

public final class WorksiteStationIndex {
    private static final long SNAPSHOT_TTL_TICKS = 20L;
    private static final int REFRESH_BUDGET_PER_TICK = 2;
    private static final Map<SnapshotKey, Snapshot> SNAPSHOTS = new ConcurrentHashMap<>();

    private WorksiteStationIndex() {}

    public static Snapshot snapshot(ServerLevel level, Set<Long> kitchenBounds) {
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

    public static void invalidate(ServerLevel level) {
        String dimensionId = level.dimension().location().toString();
        SNAPSHOTS.keySet().removeIf(key -> key.dimensionId().equals(dimensionId));
    }

    public static void invalidate(ServerLevel level, BlockPos changedPos) {
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
        List<Stations.StationSlot> stations = new ArrayList<>();
        java.util.Set<Long> claimed = new java.util.HashSet<>();
        for (long key : kitchenBounds) {
            BlockPos pos = BlockPos.of(key);
            StationType type = Stations.stationType(level, pos);
            if (type == null) continue;
            ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
            int capacity = stationCapacity(level, pos, type);
            if (capacity <= 0) continue;
            claimed.add(pos.asLong());
            stations.add(new Stations.StationSlot(pos.immutable(), type, blockId, capacity));
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
            stations.add(new Stations.StationSlot(above.immutable(),
                    StationType.PLACE_SURFACE,
                    BuiltInRegistries.BLOCK.getKey(level.getBlockState(surface).getBlock()), 1));
        }
        return new Snapshot(List.copyOf(stations), gameTime + SNAPSHOT_TTL_TICKS);
    }

    private static int stationCapacity(ServerLevel level, BlockPos pos, StationType type) {
        return switch (type) {
            case FIRE_STATION -> StationCapacities.capacity(level, pos, type);
            case HOT_STATION, CUTTING_BOARD, PASSIVE_STATION, PLACE_SURFACE, FURNACE_STATION,
                    CRAFT_SURFACE -> 1;
        };
    }

    public record Snapshot(List<Stations.StationSlot> stations, long expiresAt) {
        boolean validAt(long gameTime) {
            return gameTime <= expiresAt;
        }
    }

    private record SnapshotKey(String dimensionId, WorksiteStorageIndex.BoundsKey boundsKey) {
        static SnapshotKey create(ServerLevel level, Set<Long> kitchenBounds) {
            return new SnapshotKey(level.dimension().location().toString(), WorksiteStorageIndex.BoundsKey.of(kitchenBounds));
        }
    }

    private static Snapshot refreshSnapshotEntry(ServerLevel level, Snapshot snapshot, BlockPos changedPos) {
        List<Stations.StationSlot> refreshed = new ArrayList<>();
        for (Stations.StationSlot station : snapshot.stations()) {
            if (!station.pos().equals(changedPos)) {
                refreshed.add(station);
            }
        }
        StationType type = Stations.stationType(level, changedPos);
        if (type != null) {
            ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(changedPos).getBlock());
            int capacity = stationCapacity(level, changedPos, type);
            if (capacity > 0) {
                refreshed.add(new Stations.StationSlot(changedPos.immutable(), type, blockId, capacity));
            }
        }
        return new Snapshot(List.copyOf(refreshed), snapshot.expiresAt());
    }
}
