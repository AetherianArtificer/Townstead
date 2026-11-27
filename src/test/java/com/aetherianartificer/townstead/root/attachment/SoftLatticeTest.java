package com.aetherianartificer.townstead.root.attachment;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SoftLatticeTest {

    private static final SoftLattice.Feel MEDIUM = SoftLattice.Feel.of(0.5f);

    /** A 3x3x4 grid over a box 2 wide, 1.5 tall and 3 long behind the feet, held at its front face. */
    private static SoftLattice sac() {
        SoftLattice lattice = new SoftLattice(3, 3, 4);
        Vector3f[] goals = new Vector3f[lattice.count];
        for (int k = 0; k < 4; k++) {
            for (int j = 0; j < 3; j++) {
                for (int i = 0; i < 3; i++) {
                    goals[lattice.index(i, j, k)] = new Vector3f(-1f + i, 0.25f + 0.75f * j, 0.1f + k);
                }
            }
        }
        lattice.setGoal(goals, new Vector3f(0f, 1f, 0.1f), 0f);
        return lattice;
    }

    private static void run(SoftLattice lattice, float seconds, float[] solids, int count) {
        for (float t = 0f; t < seconds; t += 0.05f) lattice.step(0.05f, MEDIUM, 0.3f, solids, count, new float[0], 0);
    }

    @Test
    void itHangsFromItsHingeSaggingALittleButKeepingItsShape() {
        SoftLattice lattice = sac();
        run(lattice, 3f, new float[0], 0);
        int tip = lattice.index(1, 1, 3);
        float drop = lattice.goal[tip].y - lattice.pos[tip].y;
        assertTrue(drop > 0.05f && drop < 0.9f, "the free end hangs a little: " + drop);
        // Taut: the far corners stay about as far apart as at rest.
        float rest = lattice.goal[lattice.index(0, 0, 3)].distance(lattice.goal[lattice.index(2, 2, 3)]);
        float now = lattice.pos[lattice.index(0, 0, 3)].distance(lattice.pos[lattice.index(2, 2, 3)]);
        assertEquals(rest, now, 0.15f * rest, "kept its shape");
    }

    @Test
    void itTurnsAboutItsHingeAndNeverLeavesIt() {
        SoftLattice lattice = sac();
        lattice.shift(0f, 0f, 2f);
        run(lattice, 1f, new float[0], 0);
        int atHinge = lattice.index(1, 1, 0);
        assertTrue(lattice.pos[atHinge].distance(lattice.hinge) < 0.15f, "the joint stays on the hinge: " + lattice.pos[atHinge]);
        int corner = lattice.index(0, 0, 0);
        assertTrue(lattice.pos[corner].distance(lattice.goal[corner]) > 1e-3f, "the rest of the near face is free to turn");
    }

    @Test
    void aWallBehindTiltsItUpAndAwayRatherThanFlatteningIt() {
        SoftLattice lattice = sac();
        // Rock from 2 blocks behind, up to just under the sac's top: it can tilt up over it.
        float[] wall = {-5f, -2f, 2f, 5f, 1.2f, 8f};
        float[] floor = {-5f, -3f, -5f, 5f, 0f, 8f};
        float[] both = new float[12];
        System.arraycopy(wall, 0, both, 0, 6);
        System.arraycopy(floor, 0, both, 6, 6);
        run(lattice, 3f, both, 2);
        int tip = lattice.index(1, 1, 3);
        assertTrue(lattice.pos[tip].y > lattice.goal[tip].y + 0.2f, "the far end rose: " + lattice.pos[tip]);
        float rest = lattice.goal[lattice.index(0, 0, 0)].distance(lattice.goal[lattice.index(2, 2, 3)]);
        float now = lattice.pos[lattice.index(0, 0, 0)].distance(lattice.pos[lattice.index(2, 2, 3)]);
        assertTrue(now > 0.75f * rest, "turned rather than crushed: " + now + " of " + rest);
    }

    @Test
    void particlesBuriedInABlockWallComeOutTheNearSideNotIntoTheNextBlock() {
        SoftLattice lattice = sac();
        // A cliff of single blocks from half a block behind the feet: the sac's rest shape is mostly inside it.
        int blocks = 0;
        float[] cliff = new float[9 * 6 * 6 * 6];
        for (int x = -4; x < 5; x++) {
            for (int y = -1; y < 5; y++) {
                for (int z = 0; z < 6; z++) {
                    int b = blocks++ * 6;
                    cliff[b] = x; cliff[b + 1] = y; cliff[b + 2] = 0.5f + z;
                    cliff[b + 3] = x + 1; cliff[b + 4] = y + 1; cliff[b + 5] = 1.5f + z;
                }
            }
        }
        run(lattice, 4f, cliff, blocks);
        int buried = 0;
        for (int i = 0; i < lattice.count; i++) {
            org.joml.Vector3f p = lattice.pos[i];
            for (int k = 0; k < blocks; k++) {
                int b = k * 6;
                if (p.x > cliff[b] && p.x < cliff[b + 3] && p.y > cliff[b + 1] && p.y < cliff[b + 4]
                        && p.z > cliff[b + 2] && p.z < cliff[b + 5]) {
                    buried++;
                    break;
                }
            }
        }
        assertEquals(0, buried, "every particle came out of the rock");
    }

    @Test
    void movingTheBodyMakesItLagThenCatchUp() {
        SoftLattice lattice = sac();
        int tip = lattice.index(1, 1, 3);
        // The bearer jumps a block forward: the free end stays behind for a moment.
        lattice.shift(0f, 0f, 1f);
        lattice.step(0.02f, MEDIUM, 0.3f, new float[0], 0, new float[0], 0);
        float lag = lattice.pos[tip].distance(lattice.goal[tip]);
        assertTrue(lag > 0.3f, "lagged: " + lag);
        run(lattice, 4f, new float[0], 0);
        assertTrue(lattice.pos[tip].distance(lattice.goal[tip]) < 0.9f, "caught up, hanging from its hinge");
    }

    @Test
    void displacementIsZeroAtRestAndFollowsTheGrid() {
        SoftLattice lattice = sac();
        Vector3f out = new Vector3f();
        assertEquals(0f, lattice.displacement(0.4f, 0.6f, 0.7f, out).length(), 1e-6f);
        for (int i = 0; i < lattice.count; i++) lattice.pos[i].add(0f, 0.5f, 0f);
        assertEquals(0.5f, lattice.displacement(0.4f, 0.6f, 0.7f, out).y, 1e-5f);
    }

    @Test
    void theWaistOnlyBendsSoFarSoATallWallFlattensItInstead() {
        SoftLattice lattice = sac();
        // Rock from a block behind, far taller than the sac: tipping up won't clear it.
        float[] wall = {-5f, -2f, 1f, 5f, 9f, 8f};
        run(lattice, 3f, wall, 1);
        int tip = lattice.index(1, 1, 3), hinge = lattice.index(1, 1, 0);
        org.joml.Vector3f arm = new org.joml.Vector3f(lattice.pos[tip]).sub(lattice.pos[hinge]);
        double fromLevel = Math.toDegrees(Math.atan2(arm.y, Math.abs(arm.z)));
        assertTrue(fromLevel < 60.0, "never stood upright: " + fromLevel + " degrees up");
    }

    @Test
    void landingOnTheGroundDoesntBounce() {
        SoftLattice lattice = sac();
        // The ground just under its belly; drop it from half a block up and let it land.
        float[] floor = {-5f, -3f, -5f, 5f, 0.15f, 8f};
        lattice.shift(0f, 0.5f, 0f);
        run(lattice, 1.5f, floor, 1);
        float fastest = 0f;
        for (float t = 0f; t < 1f; t += 0.05f) {
            lattice.step(0.05f, MEDIUM, 0.3f, floor, 1, new float[0], 0);
            for (int i = 0; i < lattice.count; i++) fastest = Math.max(fastest, lattice.pos[i].distance(lattice.prev[i]) * 120f);
        }
        assertTrue(fastest < 0.5f, "settled rather than bouncing, fastest particle " + fastest + " blocks a second");
    }

    @Test
    void growingDoesntShakeIt() {
        SoftLattice lattice = sac();
        run(lattice, 2f, new float[0], 0);
        Vector3f[] before = new Vector3f[lattice.count], after = new Vector3f[lattice.count];
        for (int i = 0; i < lattice.count; i++) {
            before[i] = new Vector3f(lattice.goal[i]);
            // A third bigger about the hinge, all at once, as a synced value arrives.
            after[i] = new Vector3f(lattice.goal[i]).sub(lattice.hinge).mul(1.33f).add(lattice.hinge);
        }
        lattice.reshape(before, after);
        lattice.setGoal(after, lattice.hinge, 0f);
        float fastest = 0f;
        for (float t = 0f; t < 1f; t += 0.05f) {
            lattice.step(0.05f, MEDIUM, 0.3f, new float[0], 0, new float[0], 0);
            for (int i = 0; i < lattice.count; i++) fastest = Math.max(fastest, lattice.pos[i].distance(lattice.prev[i]) * 120f);
        }
        assertTrue(fastest < 0.6f, "grew without a jolt, fastest particle " + fastest + " blocks a second");
    }

    @Test
    void itKeepsOutOfALegButNotTheTorsoItHangsFrom() {
        SoftLattice lattice = sac();
        // A leg standing in the sac's way, and a torso the hinge is inside.
        ChainSim.Capsule leg = new ChainSim.Capsule("leg", new Vector3f(0f, -1f, 1.6f), new Vector3f(0f, 3f, 1.6f), 0.15f);
        ChainSim.Capsule torso = new ChainSim.Capsule("torso", new Vector3f(0f, 0.5f, 0f), new Vector3f(0f, 2f, 0f), 0.3f);
        for (float t = 0f; t < 2f; t += 0.05f) {
            lattice.step(0.05f, MEDIUM, 0.3f, new float[0], 0, new float[0], 0, java.util.List.of(leg, torso));
        }
        Vector3f near = new Vector3f();
        float closest = Float.MAX_VALUE;
        for (int i = 0; i < lattice.count; i++) closest = Math.min(closest, lattice.pos[i].distance(leg.closest(lattice.pos[i], near)));
        assertTrue(closest > 0.15f + 0.3f * 0.5f, "kept off the leg: " + closest);
        int atHinge = lattice.index(1, 1, 0);
        assertTrue(lattice.pos[atHinge].distance(lattice.hinge) < 0.2f, "still on its hinge inside the torso");
    }

    @Test
    void growingAgainstAWallStaysCalm() {
        SoftLattice lattice = sac();
        // Rock 2.5 blocks behind, floor under it; it grows from its size to 1.6 times over four seconds.
        float[] solids = {-5f, -2f, 2.5f, 5f, 6f, 8f, -5f, -3f, -5f, 5f, 0f, 8f};
        Vector3f[] rest = new Vector3f[lattice.count];
        for (int i = 0; i < lattice.count; i++) rest[i] = new Vector3f(lattice.goal[i]);
        Vector3f hinge = new Vector3f(lattice.hinge);
        Vector3f[] last = rest;
        float fastest = 0f;
        StringBuilder trace = new StringBuilder();
        for (int frame = 0; frame < 240; frame++) {
            float scale = 1f + 0.6f * Math.min(1f, frame / 240f);
            Vector3f[] goals = new Vector3f[lattice.count];
            for (int i = 0; i < lattice.count; i++) goals[i] = new Vector3f(rest[i]).sub(hinge).mul(scale).add(hinge);
            lattice.reshape(last, goals);
            lattice.setGoal(goals, hinge, 0f);
            last = goals;
            lattice.step(1f / 60f, SoftLattice.Feel.of(0.15f), 0.3f, solids, 2, new float[0], 0);
            float frameFastest = 0f;
            for (int i = 0; i < lattice.count; i++) frameFastest = Math.max(frameFastest, lattice.pos[i].distance(lattice.prev[i]) * 120f);
            // The first frames push it out of the wall it starts in; from then on it grows against it.
            if (frame < 3) continue;
            if (frameFastest > 2f && trace.length() < 400) trace.append(frame).append(':').append(String.format("%.1f ", frameFastest));
            fastest = Math.max(fastest, frameFastest);
        }
        assertTrue(fastest < 2f, "calm while growing against a wall, fastest " + fastest + " blocks a second; spikes " + trace);
    }

    @Test
    void itNeverEscapesRockIntoSpaceNobodyLookedAt() {
        SoftLattice lattice = sac();
        // A floor block right under it, and nothing gathered below that: the only way out is up.
        float[] floor = {-5f, -1f, -5f, 5f, 0.6f, 8f};
        lattice.known = new float[]{-6f, -1f, -6f, 6f, 6f, 9f};
        run(lattice, 2f, floor, 1);
        for (int i = 0; i < lattice.count; i++) {
            assertTrue(lattice.pos[i].y > 0.6f - 1e-3f, "came out on top, not under: " + lattice.pos[i]);
        }
    }

    @Test
    void walkingDownStepsItSlidesOverThemWithoutSnapping() {
        SoftLattice lattice = sac();
        // Half-block steps rising behind the body; the body walks away from them, dropping a step every 2 blocks.
        java.util.List<Float> boxes = new java.util.ArrayList<>();
        for (int k = 0; k < 10; k++) {
            float z = 1f + k;
            float top = 0.5f * (k + 1) - 0.25f;
            for (float v : new float[]{-5f, -2f, z, 5f, top, z + 1f}) boxes.add(v);
        }
        float[] steps = new float[boxes.size()];
        for (int i = 0; i < steps.length; i++) steps[i] = boxes.get(i);
        int count = steps.length / 6;
        lattice.known = new float[]{-6f, -3f, -6f, 6f, 8f, 14f};
        float fastest = 0f;
        for (int frame = 0; frame < 200; frame++) {
            // The body moves forward (away from the steps): the world, and the steps, slide back past it.
            float dz = -0.05f;
            for (int b = 0; b < count; b++) {
                steps[b * 6 + 2] -= dz;
                steps[b * 6 + 5] -= dz;
            }
            lattice.shift(0f, 0f, -dz);
            lattice.step(1f / 20f, SoftLattice.Feel.of(0.15f), 0.3f, steps, count, new float[0], 0);
            if (frame < 5) continue;
            for (int i = 0; i < lattice.count; i++) fastest = Math.max(fastest, lattice.pos[i].distance(lattice.prev[i]) * 120f);
        }
        // Riding up a half-block step is quick; a snap (a point left behind and let go) is far quicker.
        assertTrue(fastest < 8f, "slid over the steps without snapping, fastest " + fastest + " blocks a second");
    }
}
