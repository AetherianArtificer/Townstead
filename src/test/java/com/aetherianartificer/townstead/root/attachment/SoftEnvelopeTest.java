package com.aetherianartificer.townstead.root.attachment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SoftEnvelopeTest {

    private static final double EPS = 1e-6;
    // A lobe hanging behind a body that faces -z: from just behind the feet out to 3 blocks, 2 wide, 1.5 tall.
    private static final double[] LOBE = {-1.0, 0.25, 0.1, 1.0, 1.75, 3.1};
    private static final double[] ROOT = {0.0, 1.0, 0.1};

    private static double[] wallAt(double z) {
        // A tall slab of rock across the whole lobe, starting z blocks behind the feet.
        return new double[]{-5.0, -1.0, z, 5.0, 6.0, z + 1.0};
    }

    @Test
    void openGroundLeavesItAlone() {
        SoftEnvelope.Result r = SoftEnvelope.squash(LOBE, ROOT, 0.55, true, new double[0], 0);
        assertArrayEquals(LOBE, r.lobes(), EPS);
        assertEquals(0.0, r.pushX(), EPS);
        assertEquals(0.0, r.pushZ(), EPS);
        assertFalse(r.stuck());
    }

    @Test
    void aWallBehindSquashesItAndPushesTheBearerAway() {
        SoftEnvelope.Result r = SoftEnvelope.squash(LOBE, ROOT, 0.55, false, wallAt(2.0), 1);
        assertEquals(2.0 - 0.01, r.lobes()[5], 1e-4, "the far side stops at the wall");
        assertEquals(0.1, r.lobes()[2], EPS, "the root side doesn't move");
        assertTrue(r.pushZ() < 0.0, "pushed away from the wall, toward -z: " + r.pushZ());
        assertFalse(r.stuck());
    }

    @Test
    void itNeverSquashesPastItsLeast() {
        SoftEnvelope.Result r = SoftEnvelope.squash(LOBE, ROOT, 0.55, false, wallAt(0.5), 1);
        assertEquals(0.1 + 3.0 * 0.55, r.lobes()[5], 1e-4);
        assertTrue(r.stuck(), "at its least it still runs into the wall");
        assertArrayEquals(r.lobes(), r.squeezed(), 1e-4);
    }

    @Test
    void squashedItBulgesWhereThereIsRoom() {
        SoftEnvelope.Result r = SoftEnvelope.squash(LOBE, ROOT, 0.55, true, wallAt(2.0), 1);
        double width = r.lobes()[3] - r.lobes()[0];
        double height = r.lobes()[4] - r.lobes()[1];
        assertTrue(width > 2.0 + 1e-3, "wider than it was: " + width);
        assertTrue(height > 1.5 + 1e-3, "taller than it was: " + height);
        double volume = width * height * (r.lobes()[5] - r.lobes()[2]);
        assertEquals(2.0 * 1.5 * 3.0, volume, 0.05, "about the volume it had");
    }

    @Test
    void theFloorLiftsWhatHangsBelowIt() {
        double[] low = {-1.0, -0.5, 0.1, 1.0, 1.5, 3.1};
        double[] floor = {-5.0, -2.0, -5.0, 5.0, 0.0, 5.0};
        SoftEnvelope.Result r = SoftEnvelope.squash(low, new double[]{0.0, 1.0, 0.1}, 0.4, false, floor, 1);
        assertEquals(0.01, r.lobes()[1], 1e-4);
        assertEquals(0.0, r.pushX(), EPS, "the floor pushes nobody sideways");
        assertEquals(0.0, r.pushZ(), EPS);
    }

    @Test
    void aBlockBesideTheLobeDoesntSquashItAlongItsLength() {
        // Rock off to one side, not across the lobe's width: only the near side gives.
        double[] side = {1.5, -1.0, -5.0, 3.0, 6.0, 5.0};
        SoftEnvelope.Result r = SoftEnvelope.squash(LOBE, ROOT, 0.55, false, side, 1);
        assertEquals(3.1, r.lobes()[5], EPS);
        assertEquals(1.0, r.lobes()[3], EPS, "the block is past its side");
    }

    @Test
    void aSideWallPushesSideways() {
        double[] side = {0.6, -1.0, -5.0, 3.0, 6.0, 5.0};
        SoftEnvelope.Result r = SoftEnvelope.squash(LOBE, ROOT, 0.55, false, side, 1);
        assertEquals(0.59, r.lobes()[3], 1e-4);
        assertTrue(r.pushX() < 0.0, "away from a wall at +x: " + r.pushX());
    }

    @Test
    void aRootInsideRockLeavesItAtItsLeastAndStuck() {
        double[] rock = {-1.0, 0.0, -1.0, 1.0, 2.0, 1.0};
        SoftEnvelope.Result r = SoftEnvelope.squash(LOBE, ROOT, 0.5, false, rock, 1);
        assertTrue(r.stuck());
        assertEquals(0.1 + 1.5, r.lobes()[5], 1e-4);
    }

    @Test
    void turningABoxAQuarterRoundSwapsItsAxes() {
        double[] box = {-1.0, 0.0, 0.5, 1.0, 1.0, 3.0};
        double[] turned = SoftEnvelope.turned(box, 0, 90.0);
        assertArrayEquals(new double[]{-3.0, 0.0, -1.0, -0.5, 1.0, 1.0}, turned, 1e-9);
    }

    @Test
    void aWedgedSoftPartGoesThePushedWayFreelyAndNeverDeeper() {
        assertTrue(BodyVolumes.softWedgedMove(0.2, 1.0, 1.5), "with the push, even if it presses in");
        assertFalse(BodyVolumes.softWedgedMove(-0.2, 1.0, 1.0), "against the push without coming out");
        assertTrue(BodyVolumes.softWedgedMove(-0.2, 1.0, 0.8), "against the push but coming out");
        assertTrue(BodyVolumes.softWedgedMove(0.0, 1.0, 1.0), "across the push at the same depth");
        assertFalse(BodyVolumes.softWedgedMove(0.0, 1.0, 1.2), "across the push but deeper");
    }
}
