package com.aetherianartificer.townstead.chronicle.net;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Archives-building containment check: inclusive box, corner order irrelevant. */
class ChronicleArchiveAccessBoundsTest {

    @Test
    void inclusiveContainmentWithOrderedCorners() {
        assertTrue(ChronicleArchiveBounds.within(0, 60, 0, 10, 70, 10, 5, 65, 5));
        assertTrue(ChronicleArchiveBounds.within(0, 60, 0, 10, 70, 10, 0, 60, 0),
                "corners are inside");
        assertTrue(ChronicleArchiveBounds.within(0, 60, 0, 10, 70, 10, 10, 70, 10));
        assertFalse(ChronicleArchiveBounds.within(0, 60, 0, 10, 70, 10, 11, 65, 5));
        assertFalse(ChronicleArchiveBounds.within(0, 60, 0, 10, 70, 10, 5, 59, 5));
    }

    @Test
    void cornerOrderDoesNotMatter() {
        assertTrue(ChronicleArchiveBounds.within(10, 70, 10, 0, 60, 0, 3, 62, 9));
        assertFalse(ChronicleArchiveBounds.within(10, 70, 10, 0, 60, 0, 3, 71, 9));
    }
}
