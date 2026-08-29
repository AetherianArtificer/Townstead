package com.aetherianartificer.townstead.work;

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
    private static final int MAX_FRONTIER_CELLS = 4096;
    private static final int MAX_RANGE = 16;
    private static final int MAX_Y_RANGE = 5;

    private WorkSiteBounds() {}

    public static Set<Long> workArea(ServerLevel level, Building building) {
        if (level == null || building == null) return Set.of();

        // Floor-system MCA has already partitioned this place into an exact semantic footprint.
        // That includes external/open buildings such as Apiaries: "open" describes construction,
        // not an invitation to flood through the neighbourhood. Trust the recorded footprint; a
        // second range-bounded flood is both less exact and capable of crossing into another site.
        Set<Long> exact = com.aetherianartificer.townstead.compat.mca.McaBuildingCompat
                .exactWorkArea(building);
        if (!exact.isEmpty()) return exact;

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

        // Bounded flood over walkable floor: the actual room, discovered from the world. Cells
        // the flood touches but cannot stand in are the room's frontier — its walls, and more
        // importantly its furniture: stations, chests, a furnace set into the wall. Those are
        // exactly the blocks work discovery needs to see, and collecting them here is what lets
        // a newly placed station count immediately, with no building rescan and no building
        // type having to tag it. MCA's stored geometry records only each type's interest
        // blocks, which is how an armory's new furnace stayed invisible.
        Set<Long> frontier = new HashSet<>();
        while (!queue.isEmpty() && floor.size() < MAX_FLOOR_CELLS) {
            BlockPos current = queue.poll();
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos next = current.relative(dir).offset(0, dy, 0);
                    if (Math.abs(next.getX() - center.getX()) > MAX_RANGE
                            || Math.abs(next.getZ() - center.getZ()) > MAX_RANGE
                            || Math.abs(next.getY() - center.getY()) > MAX_Y_RANGE) continue;
                    if (floor.contains(next.asLong())) continue;
                    if (!WorkPathing.isSafeStandPosition(level, next)) {
                        // Only real blocks join the frontier; the air over a floor cell is
                        // unstandable too, but it is not furniture.
                        if (frontier.size() < MAX_FRONTIER_CELLS
                                && !frontier.contains(next.asLong())
                                && !level.getBlockState(next).isAir()) {
                            frontier.add(next.asLong());
                        }
                        continue;
                    }
                    floor.add(next.asLong());
                    queue.add(next);
                }
            }
        }

        // The work area is the walkable floor, the frontier it touches, and the recorded
        // furniture (station and storage discovery iterate these cells).
        Set<Long> area = new HashSet<>(floor);
        area.addAll(frontier);
        area.addAll(tagged);
        return area;
    }
}
