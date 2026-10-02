package com.aetherianartificer.townstead.compat.mca;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;

/** NBT primitives shared by Townstead's synthetic MCA building writers. */
public final class McaBuildingNbt {
    private McaBuildingNbt() {}

    // MCA 1.21 persists BlockPos through BlockPos.CODEC (an int-array). MCA 1.20's
    // Building constructor predates that codec seam and casts each entry to CompoundTag.
    //? if >=1.21 {
    public static IntArrayTag blockPos(BlockPos pos) {
        return new IntArrayTag(new int[] {pos.getX(), pos.getY(), pos.getZ()});
    }
    //?} else {
    /*public static CompoundTag blockPos(BlockPos pos) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", pos.getX());
        tag.putInt("y", pos.getY());
        tag.putInt("z", pos.getZ());
        return tag;
    }
    *///?}

    /** Defaults for an independent external/synthetic building, rather than a room floor. */
    public static void putDetachedDefaults(CompoundTag tag) {
        tag.putInt("structureId", -1);
        tag.putInt("floorId", -1);
        //? if >=1.21 {
        tag.putBoolean("contributesToMain", false);
        tag.put("floorCells", new ListTag());
        //?} else {
        /*
        tag.putBoolean("inheritanceEnabled", true);
        tag.put("floorRegions", new ListTag());
        *///?}
    }
}
