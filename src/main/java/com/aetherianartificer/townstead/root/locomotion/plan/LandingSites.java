package com.aetherianartificer.townstead.root.locomotion.plan;

import com.aetherianartificer.townstead.root.locomotion.FlightStyle;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Where to touch down for a destination. A spot needs a full top face to stand on (stair
 * and slab roofs never qualify, the slope fliers slid off), nothing hurting, no fluid, and
 * room for the body. Elytra styles also need open sky above it; soaring needs open ground
 * around it. Spots well above the destination are perches (a roof): allowed, but scored
 * behind anything on the destination's own level, since the bearer must fly down again.
 */
final class LandingSites {

    private static final int MAX_CANDIDATES = 4;
    private static final int MAX_DROP = 6;
    private static final int MAX_RISE = 8;
    private static final double PERCH_PENALTY = 12.0;
    private static final double LEAVES_PENALTY = 6.0;
    static final int PERCH_HEIGHT = 2;

    private record Scored(BlockPos pos, double score) {}

    private LandingSites() {}

    static List<BlockPos> find(AirSpace space, FlightStyle style, BlockPos destination) {
        List<Scored> found = new ArrayList<>();
        int r = style.landingRadius();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                int x = destination.getX() + dx;
                int z = destination.getZ() + dz;
                BlockPos spot = style.needsOpenSky()
                        ? skySpot(space, x, z, destination.getY())
                        : nearestSpot(space, x, z, destination.getY());
                if (spot == null) continue;
                if (style.openLanding() && !openAround(space, spot)) continue;
                int dy = spot.getY() - destination.getY();
                double score = Math.sqrt(dx * dx + dz * dz) + 2.0 * Math.abs(dy);
                if (dy > PERCH_HEIGHT) score += PERCH_PENALTY;
                if (space.isLeaves(x, spot.getY() - 1, z)) score += LEAVES_PENALTY;
                found.add(new Scored(spot, score));
            }
        }
        found.sort(Comparator.comparingDouble(Scored::score));
        List<BlockPos> out = new ArrayList<>(MAX_CANDIDATES);
        for (int i = 0; i < found.size() && out.size() < MAX_CANDIDATES; i++) out.add(found.get(i).pos());
        return out;
    }

    /** The open-sky spot on top of a column, when it is standable and near the destination's height. */
    private static BlockPos skySpot(AirSpace space, int x, int z, int destY) {
        int y = space.top(x, z);
        if (y == Integer.MAX_VALUE || y - destY > MAX_RISE || destY - y > MAX_DROP) return null;
        return space.standable(x, y, z) ? new BlockPos(x, y, z) : null;
    }

    /** The standable cell closest in height to the destination, indoors or out. */
    private static BlockPos nearestSpot(AirSpace space, int x, int z, int destY) {
        for (int d = 0; d <= MAX_RISE; d++) {
            if (space.standable(x, destY + d, z)) return new BlockPos(x, destY + d, z);
            if (d > 0 && d <= MAX_DROP && space.standable(x, destY - d, z)) return new BlockPos(x, destY - d, z);
        }
        return null;
    }

    /** Room to land long: nearly every neighbour standable at the same height, under open sky. */
    private static boolean openAround(AirSpace space, BlockPos spot) {
        int open = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                int x = spot.getX() + dx, z = spot.getZ() + dz;
                if (space.standable(x, spot.getY(), z) && space.openSky(x, spot.getY(), z)) open++;
            }
        }
        return open >= 7;
    }
}
