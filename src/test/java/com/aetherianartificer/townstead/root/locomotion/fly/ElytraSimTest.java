package com.aetherianartificer.townstead.root.locomotion.fly;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ElytraSimTest {

    @Test
    void lookFollowsVanillaAngles() {
        Vec3 south = ElytraSim.look(0f, 0f);
        assertEquals(1.0, south.z, 1.0E-6);
        Vec3 west = ElytraSim.look(0f, 90f);
        assertEquals(-1.0, west.x, 1.0E-6);
        Vec3 down = ElytraSim.look(90f, 0f);
        assertEquals(-1.0, down.y, 1.0E-6);
    }

    @Test
    void steadyGlideMatchesVanillaRatio() {
        double[] flown = glide(10f, false);
        double ratio = flown[0] / flown[1];
        // Vanilla elytra settles near 8:1 at a shallow pitch.
        assertTrue(ratio > 6.5 && ratio < 9.0, "ratio " + ratio);
    }

    @Test
    void sinkTrimStretchesTheGlide() {
        double plain = ratio(glide(10f, false));
        double trimmed = ratio(glide(10f, true));
        assertTrue(trimmed > plain, plain + " vs " + trimmed);
    }

    @Test
    void brakeShedsAtMostAFifthPerTick() {
        Vec3 slowed = ElytraSim.brake(new Vec3(1.0, -0.2, 0.0), 0.1);
        assertEquals(0.8, slowed.x, 1.0E-9);
        assertEquals(-0.2, slowed.y, 1.0E-9);
        Vec3 untouched = ElytraSim.brake(new Vec3(0.05, 0, 0), 0.1);
        assertEquals(0.05, untouched.x, 1.0E-9);
    }

    /** Forward distance and drop after 200 ticks from a cruise start. */
    private static double[] glide(float pitch, boolean assist) {
        Vec3 velocity = new Vec3(0, 0, 0.8);
        Vec3 position = Vec3.ZERO;
        for (int t = 0; t < 200; t++) {
            velocity = ElytraSim.travel(velocity, pitch, 0f);
            position = position.add(velocity);
            if (assist) velocity = ElytraSim.assist(velocity);
        }
        return new double[] {position.z, -position.y};
    }

    private static double ratio(double[] flown) {
        return flown[0] / flown[1];
    }
}
