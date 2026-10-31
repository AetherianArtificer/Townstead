package com.aetherianartificer.townstead.recognition;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Whether a room is a cavern rather than a building. Before the floor system, MCA counts any solid
 * block within 16 above as a roof, so a cave ceiling roofs a whole cavern and one door makes it a
 * room. A room roofed almost entirely by natural rock, and bigger than a room dug into a hillside,
 * is the cave itself. A small dug-in room still counts.
 */
public final class CaveRooms {
    /** The share of the roof that must be natural before a room reads as a cave. */
    static final double NATURAL_SHARE = 0.9;
    /** Open space inside a room dug into a hillside, about ten by ten and five tall. */
    static final int DUG_IN_SIZE = 512;
    /** How far above the floor MCA looks for a roof. */
    private static final int ROOF_REACH = 16;
    private static final TagKey<Block> NATURAL_CEILING = TagKey.create(
            Registries.BLOCK, ResourceLocation.tryParse("townstead:natural_ceiling"));

    private CaveRooms() {}

    /**
     * Reads the roof over every column of the room's box: the first solid block above the highest
     * open cell, as MCA finds it. {@code size} is the open space MCA counted inside.
     */
    public static boolean isCavern(ServerLevel level, BlockPos min, BlockPos max, int size) {
        if (size <= DUG_IN_SIZE) return false;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int natural = 0;
        int total = 0;
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                int top = Integer.MIN_VALUE;
                for (int y = max.getY(); y >= min.getY(); y--) {
                    cursor.set(x, y, z);
                    if (!level.isLoaded(cursor)) return false;
                    if (level.getBlockState(cursor).getCollisionShape(level, cursor).isEmpty()) {
                        top = y;
                        break;
                    }
                }
                if (top == Integer.MIN_VALUE) continue;
                for (int y = top + 1; y <= top + ROOF_REACH; y++) {
                    cursor.set(x, y, z);
                    BlockState state = level.getBlockState(cursor);
                    if (state.getCollisionShape(level, cursor).isEmpty() || state.is(BlockTags.LEAVES)) continue;
                    total++;
                    if (state.is(NATURAL_CEILING)) natural++;
                    break;
                }
            }
        }
        return isCavern(natural, total, size);
    }

    /** The rule alone: a big room under a roof of natural rock. */
    static boolean isCavern(int naturalColumns, int roofedColumns, int size) {
        return size > DUG_IN_SIZE && roofedColumns > 0 && naturalColumns >= NATURAL_SHARE * roofedColumns;
    }
}
