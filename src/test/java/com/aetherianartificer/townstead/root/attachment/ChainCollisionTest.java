package com.aetherianartificer.townstead.root.attachment;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChainCollisionTest {

    private static final float RADIUS = 0.1f;
    private static final float EPS = 1e-3f;

    @Test
    void clearSegmentIsLeftAlone() {
        Vector3f tip = new Vector3f(0f, 0f, 1f);
        assertFalse(wall(new Vector3f(), tip, 1f));
        assertEquals(new Vector3f(0f, 0f, 1f), tip);
    }

    @Test
    void tipInsideABlockTurnsBackOutThroughTheNearSide() {
        Vector3f head = new Vector3f();
        Vector3f tip = new Vector3f(2.3f, 0f, 0f);
        assertTrue(ChainCollision.outOfBox(head, tip, 2.3f, RADIUS, 2f, -1f, -1f, 3f, 1f, 1f));
        assertTrue(tip.x <= 2f - RADIUS + EPS, "tip stays on the head's side, at " + tip.x);
        assertEquals(2.3f, tip.distance(head), EPS);
    }

    @Test
    void segmentCannotTunnelThroughAThinBlock() {
        // The tip is fully past a 1/16-thick wall; a point test would never notice.
        Vector3f head = new Vector3f();
        Vector3f tip = new Vector3f(2f, 0.3f, 0f);
        boolean hit = false;
        for (int pass = 0; pass < 3; pass++) {
            hit |= ChainCollision.outOfBox(head, tip, tip.distance(head), RADIUS, 1f, -2f, -2f, 1.0625f, 2f, 2f);
        }
        assertTrue(hit);
        assertTrue(tip.x < 1f, "tip came back through the wall, at " + tip.x);
    }

    @Test
    void headInsideABlockOnlyMovesTheTip() {
        // The body stands against a wall, so the chain's root starts inside it.
        Vector3f head = new Vector3f(0.5f, 0.5f, 0.5f);
        Vector3f tip = new Vector3f(0.5f, 0.5f, 1.5f);
        assertFalse(ChainCollision.outOfBox(head, tip, 1f, RADIUS, 0f, 0f, 0f, 1f, 1f, 1f));
        assertEquals(new Vector3f(0.5f, 0.5f, 1.5f), tip);
    }

    @Test
    void capsuleStopsASegmentCrossingIt() {
        // A thin leg across the middle of the segment, with both ends clear of it.
        Vector3f head = new Vector3f(0f, 0f, -1f);
        Vector3f tip = new Vector3f(0f, 0f, 1f);
        Vector3f goal = new Vector3f(1f, 0f, 1f);
        Vector3f a = new Vector3f(0f, -1f, 0f), b = new Vector3f(0f, 1f, 0f);
        assertTrue(ChainCollision.outOfCapsule(head, tip, goal, 2f, 0.2f, a, b));
        assertTrue(tip.x > 0f, "turned toward its goal, at " + tip.x);
    }

    @Test
    void capsuleKeepsAnAuthoredPoseThatRestsInsideIt() {
        // The goal is 0.05 from the core, inside the 0.2 reach. It used to switch collision off;
        // now the segment may come that close, and no closer.
        Vector3f head = new Vector3f(1f, 0f, 0f);
        Vector3f goal = new Vector3f(0.05f, 0f, 0f);
        Vector3f a = new Vector3f(0f, -1f, 0f), b = new Vector3f(0f, 1f, 0f);
        Vector3f resting = new Vector3f(goal);
        assertFalse(ChainCollision.outOfCapsule(head, resting, goal, 0.95f, 0.2f, a, b));
        Vector3f past = new Vector3f(-0.5f, 0f, 0.01f);
        assertTrue(ChainCollision.outOfCapsule(head, past, goal, 1.5f, 0.2f, a, b));
        float[] nearest = new float[2];
        assertTrue(ChainCollision.nearest(head, past, a, b, nearest) > 0f);
    }

    @Test
    void nearestPairOfCrossingSegments() {
        float[] out = new float[2];
        float distanceSq = ChainCollision.nearest(new Vector3f(-1f, 0f, 1f), new Vector3f(1f, 0f, 1f),
                new Vector3f(0f, -1f, 0f), new Vector3f(0f, 1f, 0f), out);
        assertEquals(1f, distanceSq, EPS);
        assertEquals(0.5f, out[0], EPS);
        assertEquals(0.5f, out[1], EPS);
    }

    private static boolean wall(Vector3f head, Vector3f tip, float length) {
        return ChainCollision.outOfBox(head, tip, length, RADIUS, 2f, -1f, -1f, 3f, 1f, 1f);
    }
}
