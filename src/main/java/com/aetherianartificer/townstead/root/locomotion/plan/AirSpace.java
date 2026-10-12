package com.aetherianartificer.townstead.root.locomotion.plan;

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * World queries for one route plan, sized to one body. Positions are feet positions;
 * a cell {@code (x, y, z)} means the body standing at {@code (x + 0.5, y, z + 0.5)}.
 *
 * <p>Never loads a chunk: anything in an unloaded chunk counts as blocked. Wooden doors
 * and fence gates count as open (the mover opens them on the way through); fluids,
 * fire and other hurting blocks count as blocked.</p>
 */
public final class AirSpace {

    private static final double SEGMENT_STEP = 0.4;
    private static final int UNLOADED = Integer.MAX_VALUE;

    private final Level level;
    private final double halfWidth;
    private final double height;
    private final int minY;
    private final int maxY;
    private final Long2ByteOpenHashMap cells = new Long2ByteOpenHashMap();
    private final Long2IntOpenHashMap tops = new Long2IntOpenHashMap();

    public AirSpace(Level level, double width, double height) {
        this.level = level;
        this.halfWidth = width / 2.0;
        this.height = height;
        this.minY = level.getMinBuildHeight();
        this.maxY = level.getMaxBuildHeight() - Mth.ceil(height) - 1;
        this.tops.defaultReturnValue(Integer.MIN_VALUE);
    }

    public Level level() {
        return level;
    }

    public int minY() {
        return minY;
    }

    public int maxY() {
        return maxY;
    }

    /** Whether the bare body fits standing in this cell. Cached per plan. */
    public boolean passable(int x, int y, int z) {
        if (y < minY || y > maxY) return false;
        long key = BlockPos.asLong(x, y, z);
        byte cached = cells.get(key);
        if (cached != 0) return cached > 0;
        boolean free = boxFree(new AABB(x + 0.5 - halfWidth, y, z + 0.5 - halfWidth,
                x + 0.5 + halfWidth, y + height, z + 0.5 + halfWidth));
        cells.put(key, free ? (byte) 1 : (byte) -1);
        return free;
    }

    /** Whether the body, grown by {@code margin} on every side, can travel the straight line from a to b. */
    public boolean segmentClear(Vec3 a, Vec3 b, double margin) {
        double length = a.distanceTo(b);
        int steps = Math.max(1, Mth.ceil(length / SEGMENT_STEP));
        double hw = halfWidth + margin;
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            double x = Mth.lerp(t, a.x, b.x);
            double y = Mth.lerp(t, a.y, b.y);
            double z = Mth.lerp(t, a.z, b.z);
            if (y < minY || y > maxY) return false;
            if (!boxFree(new AABB(x - hw, y - margin * 0.5, z - hw, x + hw, y + height + margin * 0.5, z + hw))) {
                return false;
            }
        }
        return true;
    }

    /** Whether the body, grown by {@code margin}, fits at an exact feet position. Not cached. */
    public boolean bodyFree(Vec3 feet, double margin) {
        if (feet.y < minY || feet.y > maxY) return false;
        double hw = halfWidth + margin;
        return boxFree(new AABB(feet.x - hw, feet.y, feet.z - hw, feet.x + hw, feet.y + height, feet.z + hw));
    }

    /**
     * Feet height of the first free cell above the terrain at a column: the
     * {@code MOTION_BLOCKING} heightmap (leaves and fluids count). {@link Integer#MAX_VALUE}
     * when the chunk is not loaded.
     */
    public int top(int x, int z) {
        long key = BlockPos.asLong(x, 0, z);
        int cached = tops.get(key);
        if (cached != Integer.MIN_VALUE) return cached;
        int value = level.hasChunk(x >> 4, z >> 4)
                ? level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z)
                : UNLOADED;
        tops.put(key, value);
        return value;
    }

    /** Highest terrain under a body footprint grown by {@code margin}, centred on (x, z). */
    public int topUnder(double x, double z, double margin) {
        double r = halfWidth + margin;
        int x0 = Mth.floor(x - r);
        int x1 = Mth.floor(x + r);
        int z0 = Mth.floor(z - r);
        int z1 = Mth.floor(z + r);
        int best = Integer.MIN_VALUE;
        for (int bx = x0; bx <= x1; bx++) {
            for (int bz = z0; bz <= z1; bz++) {
                int t = top(bx, bz);
                if (t == UNLOADED) return UNLOADED;
                if (t > best) best = t;
            }
        }
        return best;
    }

    /** Whether nothing above this cell blocks the sky (open-sky takeoffs and landings). */
    public boolean openSky(int x, int y, int z) {
        int t = top(x, z);
        return t != UNLOADED && t <= y;
    }

    /**
     * Whether a body can stand in this cell: a full top face below, nothing hurting
     * underfoot or at the feet, no fluid, and room for the body.
     */
    public boolean standable(int x, int y, int z) {
        if (!passable(x, y, z)) return false;
        BlockPos below = new BlockPos(x, y - 1, z);
        if (!level.hasChunkAt(below)) return false;
        BlockState floor = level.getBlockState(below);
        if (!floor.isFaceSturdy(level, below, Direction.UP)) return false;
        if (hurts(floor) || !level.getFluidState(below).isEmpty()) return false;
        return true;
    }

    /** Free cells beneath a cell before the first solid or fluid one, capped at {@code max}. */
    public int airBelow(int x, int y, int z, int max) {
        for (int d = 1; d <= max; d++) {
            BlockPos pos = new BlockPos(x, y - d, z);
            if (!level.hasChunkAt(pos)) return d - 1;
            BlockState state = level.getBlockState(pos);
            if (!state.getCollisionShape(level, pos).isEmpty() || !level.getFluidState(pos).isEmpty()) return d - 1;
        }
        return max;
    }

    public boolean isLeaves(int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        return level.hasChunkAt(pos) && level.getBlockState(pos).is(BlockTags.LEAVES);
    }

    private boolean boxFree(AABB box) {
        int x0 = Mth.floor(box.minX);
        int x1 = Mth.floor(box.maxX - 1.0E-7);
        int y0 = Mth.floor(box.minY);
        int y1 = Mth.floor(box.maxY - 1.0E-7);
        int z0 = Mth.floor(box.minZ);
        int z1 = Mth.floor(box.maxZ - 1.0E-7);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        // Collision shapes can reach half a block below their cell (fences, walls).
        for (int bx = x0; bx <= x1; bx++) {
            for (int bz = z0; bz <= z1; bz++) {
                for (int by = y0 - 1; by <= y1; by++) {
                    pos.set(bx, by, bz);
                    if (!level.hasChunkAt(pos)) return false;
                    BlockState state = level.getBlockState(pos);
                    if (by >= y0) {
                        if (!level.getFluidState(pos).isEmpty() || hurts(state)) return false;
                    }
                    if (state.isAir() || passesThrough(state)) continue;
                    VoxelShape shape = state.getCollisionShape(level, pos);
                    if (shape.isEmpty()) continue;
                    for (AABB part : shape.toAabbs()) {
                        if (part.move(bx, by, bz).intersects(box)) return false;
                    }
                }
            }
        }
        return true;
    }

    private static boolean passesThrough(BlockState state) {
        return state.is(BlockTags.WOODEN_DOORS) || state.is(BlockTags.FENCE_GATES);
    }

    static boolean hurts(BlockState state) {
        return state.is(BlockTags.FIRE) || state.is(BlockTags.CAMPFIRES)
                || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CACTUS)
                || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.POWDER_SNOW)
                || state.is(Blocks.WITHER_ROSE) || state.is(Blocks.POINTED_DRIPSTONE);
    }
}
