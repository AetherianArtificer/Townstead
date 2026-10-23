package com.aetherianartificer.townstead.village;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Archives-building containment check: inclusive box, corner order irrelevant. */
class ArchivesBuildingBoundsTest {

    @Test
    void inclusiveContainmentWithOrderedCorners() {
        assertTrue(ArchivesBuilding.within(0, 60, 0, 10, 70, 10, 5, 65, 5));
        assertTrue(ArchivesBuilding.within(0, 60, 0, 10, 70, 10, 0, 60, 0), "corners are inside");
        assertTrue(ArchivesBuilding.within(0, 60, 0, 10, 70, 10, 10, 70, 10));
        assertFalse(ArchivesBuilding.within(0, 60, 0, 10, 70, 10, 11, 65, 5));
        assertFalse(ArchivesBuilding.within(0, 60, 0, 10, 70, 10, 5, 59, 5));
    }

    @Test
    void cornerOrderDoesNotMatter() {
        assertTrue(ArchivesBuilding.within(10, 70, 10, 0, 60, 0, 3, 62, 9));
        assertFalse(ArchivesBuilding.within(10, 70, 10, 0, 60, 0, 3, 71, 9));
    }
}
