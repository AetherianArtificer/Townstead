package com.aetherianartificer.townstead.building.pin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BuildingPinProgressPolicyTest {
    @Test
    void carriedCreativeItemDoesNotCountAsPlacedBuildingProgress() {
        assertEquals(1, BuildingPinProgressPolicy.countedBlocks(1, 1));
    }

    @Test
    void placingAnotherStationIncrementsProgressExactlyOnce() {
        assertEquals(2, BuildingPinProgressPolicy.countedBlocks(2, 1));
    }

    @Test
    void clampsMalformedNegativePlacedCounts() {
        assertEquals(0, BuildingPinProgressPolicy.countedBlocks(-1, 64));
    }
}
