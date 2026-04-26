package com.aetherianartificer.townstead.dock;

import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Read-only dock lookup for villager work tasks.
 *
 * Important contract: {@link #nearby(ServerLevel, BlockPos, int)} must never
 * scan blocks, query MCA villages, or rebuild anything. It only reads snapshots
 * that were populated by dock/building lifecycle code.
 */
public final class DockLocationIndex {
    private static final Map<String, Map<Integer, List<Dock>>> BY_VILLAGE = new ConcurrentHashMap<>();

    private DockLocationIndex() {}

    public static void rebuildVillage(ServerLevel level, Village village) {
        if (level == null || village == null) return;
        List<Dock> docks = new ArrayList<>();
        for (Building building : village.getBuildings().values()) {
            Dock dock = dockFromBuilding(building);
            if (dock != null) docks.add(dock);
        }
        docks.sort(Comparator
                .comparingInt((Dock dock) -> dock.bounds().minX())
                .thenComparingInt(dock -> dock.bounds().minY())
                .thenComparingInt(dock -> dock.bounds().minZ()));
        BY_VILLAGE
                .computeIfAbsent(level.dimension().location().toString(), k -> new ConcurrentHashMap<>())
                .put(village.getId(), List.copyOf(docks));
    }

    public static List<Dock> nearby(ServerLevel level, BlockPos origin, int radius) {
        if (level == null || origin == null || radius < 0) return List.of();
        Map<Integer, List<Dock>> byVillage = BY_VILLAGE.get(level.dimension().location().toString());
        if (byVillage == null || byVillage.isEmpty()) return List.of();
        int radiusSq = radius * radius;
        List<Dock> out = new ArrayList<>();
        for (List<Dock> docks : byVillage.values()) {
            for (Dock dock : docks) {
                if (dock == null || dock.bounds() == null) continue;
                if (horizontalDistanceSq(origin, dock.bounds()) <= radiusSq) {
                    out.add(dock);
                }
            }
        }
        return out;
    }

    private static Dock dockFromBuilding(Building building) {
        if (building == null) return null;
        String type = building.getType();
        int tier = tierOf(type);
        if (tier <= 0) return null;
        BlockPos p0 = building.getPos0();
        BlockPos p1 = building.getPos1();
        BoundingBox bounds = new BoundingBox(
                Math.min(p0.getX(), p1.getX()),
                Math.min(p0.getY(), p1.getY()),
                Math.min(p0.getZ(), p1.getZ()),
                Math.max(p0.getX(), p1.getX()),
                Math.max(p0.getY(), p1.getY()),
                Math.max(p0.getZ(), p1.getZ()));
        return new Dock(bounds, Math.max(1, building.getSize()), tier);
    }

    private static int tierOf(String type) {
        if (type == null || !type.startsWith("dock_l")) return 0;
        String suffix = type.substring("dock_l".length());
        if (suffix.isEmpty() || !suffix.chars().allMatch(Character::isDigit)) return 0;
        try {
            return Integer.parseInt(suffix);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static int horizontalDistanceSq(BlockPos origin, BoundingBox bb) {
        int dx = axisDistance(origin.getX(), bb.minX(), bb.maxX());
        int dz = axisDistance(origin.getZ(), bb.minZ(), bb.maxZ());
        return dx * dx + dz * dz;
    }

    private static int axisDistance(int value, int min, int max) {
        if (value < min) return min - value;
        if (value > max) return value - max;
        return 0;
    }
}
