package com.aetherianartificer.townstead.work.station;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StationDropOutputsTest {

    @Test
    void acceptsDropsAboveAWorksiteCell() {
        Set<Long> worksite = Set.of(BlockPos.asLong(10, 64, 20));

        assertTrue(StationDropOutputs.insideWorksiteColumn(BlockPos.of(BlockPos.asLong(10, 67, 20)), worksite));
    }

    @Test
    void rejectsDropsAcrossAHorizontalWorksiteBoundary() {
        Set<Long> worksite = Set.of(BlockPos.asLong(10, 64, 20));

        assertFalse(StationDropOutputs.insideWorksiteColumn(BlockPos.of(BlockPos.asLong(11, 64, 20)), worksite));
    }

    @Test
    void rejectsUnreasonablyDistantVerticalDrops() {
        Set<Long> worksite = Set.of(BlockPos.asLong(10, 64, 20));

        assertFalse(StationDropOutputs.insideWorksiteColumn(BlockPos.of(BlockPos.asLong(10, 68, 20)), worksite));
    }
}
