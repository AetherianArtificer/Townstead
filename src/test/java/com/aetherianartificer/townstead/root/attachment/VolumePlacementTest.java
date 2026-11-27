package com.aetherianartificer.townstead.root.attachment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VolumePlacementTest {

    private static final float[] NO_PIVOT = {0f, 0f, 0f};
    private static final float[] NO_GROW = {1f, 1f, 1f};
    // A box 8px wide, 8px tall, and 16px behind the body.
    private static final float[] BEHIND = {-4f, 8f, 0f, 4f, 16f, 16f};

    @Test
    void facingSouthThePartIsNorthOfTheBody() {
        // Yaw 0 looks toward +z, so behind the body is -z.
        double[] box = VolumePlacement.place(BEHIND, NO_PIVOT, NO_GROW, 1f, 1f, 0f);
        assertArrayEquals(new double[]{-0.25, 0.5, -1.0, 0.25, 1.0, 0.0}, box, 1e-6);
    }

    @Test
    void facingWestThePartIsEastOfTheBody() {
        // Yaw 90 looks toward -x, so behind the body is +x.
        double[] box = VolumePlacement.place(BEHIND, NO_PIVOT, NO_GROW, 1f, 1f, 90f);
        assertEquals(0.0, box[0], 1e-6);
        assertEquals(1.0, box[3], 1e-6);
        assertEquals(-0.25, box[2], 1e-6);
        assertEquals(0.25, box[5], 1e-6);
    }

    @Test
    void growingScalesAboutThePivot() {
        float[] pivot = {0f, 8f, 0f};
        float[] twice = {2f, 2f, 2f};
        double[] box = VolumePlacement.place(BEHIND, pivot, twice, 1f, 1f, 0f);
        assertEquals(0.5, box[1], 1e-6, "the pivot's height stays put");
        assertEquals(1.5, box[4], 1e-6);
        assertEquals(-2.0, box[2], 1e-6);
        assertEquals(-0.5, box[0], 1e-6);
    }

    @Test
    void anEmptyBarCanShrinkThePartBelowDoorWidth() {
        // A part 20px wide is wider than a door until its fill drops under 0.8.
        float[] wide = {-10f, 8f, 0f, 10f, 24f, 16f};
        float factor = VolumePlacement.growFactor(0.7f, 1f);
        double[] box = VolumePlacement.place(wide, NO_PIVOT, new float[]{factor, 1f, factor}, 1f, 1f, 0f);
        assertTrue(box[3] - box[0] < 1.0, "width " + (box[3] - box[0]));
    }

    @Test
    void wedgedBoxMayLeaveButNotGoDeeper() {
        assertTrue(VolumePlacement.wedgedMoveAllowed(0.5, 0.2));
        assertTrue(VolumePlacement.wedgedMoveAllowed(0.5, 0.5));
        assertFalse(VolumePlacement.wedgedMoveAllowed(0.5, 0.7));
    }
}
