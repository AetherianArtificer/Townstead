package com.aetherianartificer.townstead.enclosure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Horizontal flood-fill detector for fenced-and-gated enclosures. Given a
 * player position, scans outward through passable interior blocks (air, tall
 * grass, vines, farmland, paths) and terminates cleanly at a ring of fences,
 * fence-gates, walls, or solid blocks. Returns {@code null} when the fill
 * escapes the scan bound (not closed), hits no perimeter blocks at all, or
 * the interior is below the global minimum.
 *
 * <p>Horizontal plane only — the scan happens at the player's feet-Y level
 * (shifting up or down one block if the feet-level block is inside a fence
 * or a ceiling block). Content tallying sweeps the interior column from the
 * floor to a few blocks above so hay bales, blood grates, lanterns, and
 * similar signature blocks placed inside register correctly.
 */
public final class EnclosureScanner {
    private static final Logger LOG = LoggerFactory.getLogger("Townstead/EnclosureScanner");

    private static final int HORIZONTAL_RADIUS = 48;
    // Below this interior tile count, don't even consider classification
    // candidates — smaller areas are almost always false positives (a gap
    // between two fences, a corner accidentally enclosed). Per-type
    // minInterior may raise this further.
    private static final int GLOBAL_MIN_INTERIOR = 4;
    // Upper bound on a single enclosure; beyond this the flood is almost
    // certainly crossing into unfenced terrain. Generous so sprawling
    // livestock compounds register — 4096 is a ~64×64 pen, way past a
    // reasonable build.
    private static final int MAX_INTERIOR_TILES = 4096;
    // Tally blocks at dy=-1 only if they match a registered signature (e.g.
    // blood_grate placed flush with the ground). That way grass, dirt, paths,
    // and farmland stay out of the tally while floor-level signature blocks
    // still register. Blocks at dy=0..up are tallied unconditionally so
    // above-ground furniture (hay bales, lanterns) is always captured.
    private static final int CONTENT_SCAN_DOWN = 1;
    private static final int CONTENT_SCAN_UP = 3;

    private EnclosureScanner() {}

    public static @Nullable Enclosure scan(ServerLevel level, BlockPos from) {
        if (level == null || from == null) return null;
        BlockPos origin = pickScanOrigin(level, from);
        if (origin == null) {
            LOG.debug("[EnclosureScan] reject at {} — no passable origin", from);
            return null;
        }

        Set<BlockPos> interior = new HashSet<>();
        Set<BlockPos> perimeter = new HashSet<>();
        // Solid blocks that aren't fences/gates/walls but sit inside the
        // flood (hay bales, blood grates, troughs, barrels). They stop
        // flow like a perimeter block, but they're actually pen furniture
        // and must be tallied as interior content — classification depends
        // on them.
        Set<BlockPos> interiorFurniture = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(origin);

        while (!queue.isEmpty()) {
            BlockPos cur = queue.poll();
            if (interior.contains(cur)) continue;
            if (Math.abs(cur.getX() - origin.getX()) > HORIZONTAL_RADIUS
                    || Math.abs(cur.getZ() - origin.getZ()) > HORIZONTAL_RADIUS) {
                // Flood escaped the scan box — the enclosure isn't closed.
                LOG.info("[EnclosureScan] reject at {} — flood escaped {}-radius box (not closed)",
                        from, HORIZONTAL_RADIUS);
                return null;
            }
            if (interior.size() >= MAX_INTERIOR_TILES) {
                LOG.info("[EnclosureScan] reject at {} — interior exceeded {} tiles", from, MAX_INTERIOR_TILES);
                return null;
            }
            interior.add(cur);

            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos n = cur.relative(d);
                if (interior.contains(n)) continue;
                BlockState ns = level.getBlockState(n);
                if (isPerimeterBlock(ns)) {
                    perimeter.add(n);
                } else if (isPassableInterior(ns)) {
                    queue.add(n);
                } else {
                    // Solid non-perimeter block inside the flood: pen furniture.
                    // Stops flow (so the enclosure can still be closed) but
                    // counts toward interior content for classification.
                    interiorFurniture.add(n);
                }
            }
        }

        if (interior.size() < GLOBAL_MIN_INTERIOR) {
            LOG.info("[EnclosureScan] reject at {} — interior size {} < min {}",
                    from, interior.size(), GLOBAL_MIN_INTERIOR);
            return null;
        }
        if (perimeter.isEmpty()) {
            LOG.info("[EnclosureScan] reject at {} — no perimeter blocks", from);
            return null;
        }

        int fences = 0;
        int gates = 0;
        int walls = 0;
        for (BlockPos p : perimeter) {
            BlockState s = level.getBlockState(p);
            if (s.is(BlockTags.FENCE_GATES)) gates++;
            else if (s.is(BlockTags.FENCES)) fences++;
            else if (s.is(BlockTags.WALLS)) walls++;
        }
        // An enclosure with no fence-like perimeter blocks at all is some
        // random crevice in terrain; require at least one fence/gate/wall.
        if (fences + gates + walls == 0) {
            LOG.info("[EnclosureScan] reject at {} — perimeter has no fences/gates/walls", from);
            return null;
        }

        Map<String, Integer> content = tallyInteriorContent(level, interior, interiorFurniture);
        BoundingBox bounds = computeBounds(interior, perimeter, interiorFurniture);
        Set<BlockPos> fullInterior = new HashSet<>(interior);
        fullInterior.addAll(interiorFurniture);
        LOG.info("[EnclosureScan] accept at {} — interior={} furniture={} perimeter={} fences={} gates={} walls={}",
                from, interior.size(), interiorFurniture.size(), perimeter.size(), fences, gates, walls);
        return new Enclosure(bounds, fullInterior, perimeter, fences, gates, walls, content);
    }

    /**
     * If the block at the player's feet is a fence/wall/gate or a solid
     * ceiling block, shift up or down by one to find a passable interior
     * tile. Returns {@code null} if neither feet-level nor adjacent Y is
     * valid (the player probably isn't inside anything pen-shaped).
     */
    private static @Nullable BlockPos pickScanOrigin(ServerLevel level, BlockPos near) {
        BlockPos here = new BlockPos(near.getX(), near.getY(), near.getZ());
        if (isPassableInterior(level.getBlockState(here))) return here;
        BlockPos up = here.above();
        if (isPassableInterior(level.getBlockState(up))) return up;
        BlockPos down = here.below();
        if (isPassableInterior(level.getBlockState(down))) return down;
        return null;
    }

    /**
     * Anything the scan treats as interior: air and the "replaceable" family
     * (tall grass, flowers, snow layers, water) — blocks a player could
     * plausibly walk through within a pen. Fences/gates/walls are perimeter,
     * everything solid is perimeter. Doors are solid so they stop the fill
     * the same way a gate does.
     */
    private static boolean isPassableInterior(BlockState state) {
        if (state.isAir()) return true;
        if (state.canBeReplaced()) return true;
        return false;
    }

    private static boolean isPerimeterBlock(BlockState state) {
        return state.is(BlockTags.FENCES)
                || state.is(BlockTags.FENCE_GATES)
                || state.is(BlockTags.WALLS);
    }

    private static Map<String, Integer> tallyInteriorContent(ServerLevel level,
            Set<BlockPos> interior, Set<BlockPos> interiorFurniture) {
        Map<String, Integer> counts = new HashMap<>();
        for (BlockPos p : interior) {
            for (int dy = -CONTENT_SCAN_DOWN; dy <= CONTENT_SCAN_UP; dy++) {
                BlockPos q = p.offset(0, dy, 0);
                BlockState s = level.getBlockState(q);
                if (s.isAir()) continue;
                if (dy < 0 && !EnclosureTypeIndex.anySpecRequires(s)) continue;
                ResourceLocation id = BuiltInRegistries.BLOCK.getKey(s.getBlock());
                counts.merge(id.toString(), 1, Integer::sum);
            }
        }
        for (BlockPos p : interiorFurniture) {
            BlockState s = level.getBlockState(p);
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(s.getBlock());
            counts.merge(id.toString(), 1, Integer::sum);
        }
        return counts;
    }

    private static BoundingBox computeBounds(Set<BlockPos> interior,
            Set<BlockPos> perimeter, Set<BlockPos> interiorFurniture) {
        int[] box = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE,
                     Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        expandBox(box, interior);
        expandBox(box, perimeter);
        expandBox(box, interiorFurniture);
        return new BoundingBox(box[0], box[1] - 1, box[2], box[3], box[4] + CONTENT_SCAN_UP, box[5]);
    }

    private static void expandBox(int[] box, Set<BlockPos> positions) {
        for (BlockPos p : positions) {
            if (p.getX() < box[0]) box[0] = p.getX();
            if (p.getY() < box[1]) box[1] = p.getY();
            if (p.getZ() < box[2]) box[2] = p.getZ();
            if (p.getX() > box[3]) box[3] = p.getX();
            if (p.getY() > box[4]) box[4] = p.getY();
            if (p.getZ() > box[5]) box[5] = p.getZ();
        }
    }
}
