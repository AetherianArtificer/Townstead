package com.aetherianartificer.townstead.compat.mca;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;

/** NBT primitives shared by Townstead's synthetic MCA building writers. */
public final class McaBuildingNbt {
    private McaBuildingNbt() {}

    /**
     * MCA persists BlockPos through {@code BlockPos.CODEC}. With NbtOps that is an int-array,
     * not a compound containing x/y/z. MCA drops positions it cannot decode, leaving a building
     * record present but with an empty block map.
     */
    public static IntArrayTag blockPos(BlockPos pos) {
        return new IntArrayTag(new int[] {pos.getX(), pos.getY(), pos.getZ()});
    }

    /** Defaults for an independent external/synthetic building, rather than a room floor. */
    public static void putDetachedDefaults(CompoundTag tag) {
        tag.putInt("structureId", -1);
        tag.putInt("floorId", -1);
        tag.putBoolean("inheritanceEnabled", true);
        tag.put("floorRegions", new ListTag());
    }
}
