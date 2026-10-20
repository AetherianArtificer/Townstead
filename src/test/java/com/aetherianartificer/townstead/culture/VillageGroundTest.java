package com.aetherianartificer.townstead.culture;

import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VillageGroundTest {
    private static final BoundingBox HOUSE = new BoundingBox(0, 64, 0, 8, 72, 8);

    @Test
    void standsOnLevelGround() {
        assertTrue(VillageGround.fits(HOUSE, false, ground((x, z) -> 65, (x, z) -> 65)));
    }

    @Test
    void groundAFewBlocksDownIsRaisedToMeetIt() {
        assertTrue(VillageGround.fits(HOUSE, false, ground((x, z) -> 62, (x, z) -> 62)));
    }

    @Test
    void leavesOutABuildingOverALake() {
        // The east two thirds of the footprint is water.
        VillageGround.Heights lake = ground((x, z) -> 63, (x, z) -> x > 2 ? 58 : 63);
        assertFalse(VillageGround.fits(HOUSE, false, lake));
        assertTrue(VillageGround.fits(HOUSE, true, lake), "a stilt hut may stand in water");
    }

    @Test
    void leavesOutABuildingOffACliff() {
        VillageGround.Heights cliff = ground((x, z) -> z > 2 ? 50 : 65, (x, z) -> z > 2 ? 50 : 65);
        assertFalse(VillageGround.fits(HOUSE, false, cliff));
    }

    @Test
    void toleratesACornerOverTheEdge() {
        VillageGround.Heights corner = ground((x, z) -> x > 6 && z > 6 ? 50 : 65, (x, z) -> x > 6 && z > 6 ? 50 : 65);
        assertTrue(VillageGround.fits(HOUSE, false, corner));
    }

    private interface Column {
        int at(int x, int z);
    }

    private static VillageGround.Heights ground(Column surface, Column floor) {
        return new VillageGround.Heights() {
            @Override
            public int surface(int x, int z) {
                return surface.at(x, z);
            }

            @Override
            public int floor(int x, int z) {
                return floor.at(x, z);
            }
        };
    }
}
