package com.aetherianartificer.townstead.compat.mca;

import com.aetherianartificer.townstead.village.TownsteadVillageSavedData;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McaBuildingsContainmentTest {
    @Test
    void lShapedDockDoesNotClaimWaterInsideItsBoundingRectangle() {
        TownsteadVillageSavedData.BuildingOverlay dock = new TownsteadVillageSavedData.BuildingOverlay(
                "dock", "dock_l1", new int[] {0, 60, 0, 2, 60, 2},
                Map.of("minecraft:oak_planks", new long[] {
                        new BlockPos(0, 60, 0).asLong(),
                        new BlockPos(1, 60, 0).asLong(),
                        new BlockPos(0, 60, 1).asLong()
                }));

        assertTrue(SyntheticBuildingGeometry.contains(dock, new BlockPos(1, 61, 0)));
        assertFalse(SyntheticBuildingGeometry.contains(dock, new BlockPos(1, 61, 1)));
    }

    @Test
    void enclosureClaimsItsSavedInteriorAndPlayerHeight() {
        TownsteadVillageSavedData.BuildingOverlay enclosure = new TownsteadVillageSavedData.BuildingOverlay(
                "enclosure", "pen", new int[] {10, 64, 10, 15, 65, 15}, Map.of());

        assertTrue(SyntheticBuildingGeometry.contains(enclosure, new BlockPos(12, 66, 12)));
        assertFalse(SyntheticBuildingGeometry.contains(enclosure, new BlockPos(16, 64, 12)));
    }
}
