package com.aetherianartificer.townstead.ai.work;

import net.conczin.mca.server.world.data.Building;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * The cell set work navigation reasons over for a building worksite. Standable tiles, station
 * stands, flood connectivity, and arrival ("is the villager at the worksite") all derive from
 * this set, so it must be the room a villager can actually stand in. MCA's stored geometry
 * cannot provide that: grouped building types (kitchens, cafes) record only their tagged
 * furniture blocks, and {@code getPos0()}/{@code getPos1()} is just that cluster's bounding box.
 * So the area is derived from the physical world instead: a bounded flood-fill of walkable
 * floor cells outward from the furniture, plus the furniture cells themselves.
 */
public final class WorkSiteBounds {

    private static final int MAX_FLOOR_CELLS = 2048;
    private static final int MAX_RANGE = 16;
    private static final int MAX_Y_RANGE = 5;

    private WorkSiteBounds() {}

    public static Set<Long> workArea(ServerLevel level, Building building) {
        if (level == null || building == null) return Set.of();

        Set<Long> tagged = new HashSet<>();
        building.getBlockPosStream().forEach(pos -> tagged.add(pos.asLong()));
        BlockPos center = building.getCenter();
        if (center == null && !tagged.isEmpty()) center = BlockPos.of(tagged.iterator().next());
        return workArea(level, tagged, center);
    }

    /**
     * Work area for a site that is not a building at all: a standalone workstation (an outdoor
     * cooking post) anchors the same walkable-floor flood-fill a building's furniture would.
     */
    public static Set<Long> workAreaAround(ServerLevel level, BlockPos anchor) {
        if (level == null || anchor == null) return Set.of();
        return workArea(level, Set.of(anchor.asLong()), anchor);
    }

    private static Set<Long> workArea(ServerLevel level, Set<Long> tagged, BlockPos center) {
        if (tagged.isEmpty() || center == null) return Set.of();

        // Seeds: any safe standing cell touching a tagged block (beside it at foot level, a
        // step up or down, or directly above low furniture is fine for seeding only if legal).
        Deque<BlockPos> queue = new ArrayDeque<>();
        Set<Long> floor = new HashSet<>();
        for (long key : tagged) {
            BlockPos pos = BlockPos.of(key);
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                for (int dy = -2; dy <= 1; dy++) {
                    BlockPos candidate = pos.relative(dir).offset(0, dy, 0);
                    if (floor.contains(candidate.asLong())) continue;
                    if (!WorkPathing.isSafeStandPosition(level, candidate)) continue;
                    floor.add(candidate.asLong());
                    queue.add(candidate);
                }
            }
        }

        // Bounded flood over walkable floor: the actual room, discovered from the world.
        while (!queue.isEmpty() && floor.size() < MAX_FLOOR_CELLS) {
            BlockPos current = queue.poll();
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos next = current.relative(dir).offset(0, dy, 0);
                    if (Math.abs(next.getX() - center.getX()) > MAX_RANGE
                            || Math.abs(next.getZ() - center.getZ()) > MAX_RANGE
                            || Math.abs(next.getY() - center.getY()) > MAX_Y_RANGE) continue;
                    if (floor.contains(next.asLong())) continue;
                    if (!WorkPathing.isSafeStandPosition(level, next)) continue;
                    floor.add(next.asLong());
                    queue.add(next);
                }
            }
        }

        // The work area is the walkable floor plus the furniture itself (station and storage
        // discovery iterate these cells).
        Set<Long> area = new HashSet<>(floor);
        area.addAll(tagged);
        return area;
    }
}
