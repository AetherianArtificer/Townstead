package com.aetherianartificer.townstead.culture;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Sets a culture village building on the ground. Worldgen raises the ground to meet a building only
 * part of the way on a slope; under each full block of the building's bottom layer that still stands
 * over air or water, this fills down to the ground: dirt under an earthen floor, cobblestone under
 * anything else. A building meant to stand in water keeps its water.
 */
public final class VillageFoundations {
    /** How far down a foundation reaches; a building over a deeper drop is left out of the village. */
    public static final int DEPTH = 8;

    private VillageFoundations() {}

    /** Fills the foundation of one building, within the part of the world being placed. */
    public static void fill(WorldGenLevel level, BoundingBox building, BoundingBox area, boolean wet) {
        int minX = Math.max(building.minX(), area.minX()), maxX = Math.min(building.maxX(), area.maxX());
        int minZ = Math.max(building.minZ(), area.minZ()), maxZ = Math.min(building.maxZ(), area.maxZ());
        int y = building.minY();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                BlockState bottom = level.getBlockState(pos.set(x, y, z));
                if (bottom.isAir() || !bottom.getFluidState().isEmpty() || !bottom.isCollisionShapeFullBlock(level, pos)) continue;
                BlockState footing = (bottom.is(BlockTags.DIRT) ? Blocks.DIRT : Blocks.COBBLESTONE).defaultBlockState();
                for (int d = 1; d <= DEPTH; d++) {
                    BlockState there = level.getBlockState(pos.set(x, y - d, z));
                    boolean fluid = !there.getFluidState().isEmpty();
                    if (fluid ? wet : !there.isAir() && !there.canBeReplaced()) break;
                    level.setBlock(pos, footing, 2);
                }
            }
        }
    }
}
