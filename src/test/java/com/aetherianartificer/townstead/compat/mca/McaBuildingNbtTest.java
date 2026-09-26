package com.aetherianartificer.townstead.compat.mca;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McaBuildingNbtTest {
    @Test
    void syntheticBlockPositionsUseMcasBlockPosCodecShape() {
        BlockPos expected = new BlockPos(-490, 64, 326);

        //? if >=1.21 {
        assertArrayEquals(new int[] {-490, 64, 326},
                McaBuildingNbt.blockPos(expected).getAsIntArray());
        //?} else {
        /*var encoded = McaBuildingNbt.blockPos(expected);
        assertEquals(-490, encoded.getInt("x"));
        assertEquals(64, encoded.getInt("y"));
        assertEquals(326, encoded.getInt("z"));
        *///?}
    }

    @Test
    void detachedDefaultsUseTheInstalledMcaBuildingSchema() {
        CompoundTag tag = new CompoundTag();
        McaBuildingNbt.putDetachedDefaults(tag);

        assertEquals(-1, tag.getInt("structureId"));
        assertEquals(-1, tag.getInt("floorId"));
        //? if >=1.21 {
        assertTrue(tag.contains("floorCells", Tag.TAG_LIST));
        assertFalse(tag.getBoolean("contributesToMain"));

        //?} else {
        /*assertTrue(tag.contains("floorRegions"));
        assertTrue(tag.getBoolean("inheritanceEnabled"));
        *///?}
    }
}
