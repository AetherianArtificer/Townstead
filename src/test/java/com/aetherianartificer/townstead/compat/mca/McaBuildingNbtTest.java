package com.aetherianartificer.townstead.compat.mca;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class McaBuildingNbtTest {
    @Test
    void syntheticBlockPositionsUseMcasBlockPosCodecShape() {
        BlockPos expected = new BlockPos(-490, 64, 326);

        assertArrayEquals(new int[] {-490, 64, 326},
                McaBuildingNbt.blockPos(expected).getAsIntArray());
    }
}
