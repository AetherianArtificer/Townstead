package com.aetherianartificer.townstead.root.rig;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BodySizeTest {
    private static final RigDefinition.Hitbox FROG = new RigDefinition.Hitbox(.5f, .75f, .625f);
    @Test void growthAndIndependentProportionsAreAppliedBeforeTheSafetyCaps() {
        var small = BodySize.resolve(FROG, .5, .8, BodySize.Posture.STANDING);
        assertEquals(.25f, small.width(), .00001);
        assertEquals(.6f, small.height(), .00001);
        var giant = BodySize.resolve(FROG, 10, 10, BodySize.Posture.STANDING);
        assertEquals(.6f, giant.width());
        assertEquals(1.9f, giant.height());
        assertTrue(giant.eyeHeight() < giant.height());
    }
    @Test void crouchKeepsItsRatioAfterClampingAndSwimmingHasIndependentClearance() {
        var crouch = BodySize.resolve(FROG, 10, 10, BodySize.Posture.CROUCHING);
        assertEquals(1.9f * .625f / .75f, crouch.height(), .00001);
        var swim = BodySize.resolve(FROG, 1, 1, BodySize.Posture.SWIMMING);
        assertEquals(.6f, swim.height(), .00001);
        var malformedPose = new RigDefinition.Hitbox(.5f, .75f, 99, 99, 99, true);
        for (var pose : BodySize.Posture.values()) {
            var size = BodySize.resolve(malformedPose, 1, 1, pose);
            assertEquals(.75f, size.height());
            assertEquals(.7f, size.eyeHeight(), .00001);
        }
    }
    @Test void invalidDefinitionsAreRejectedAndScaleInputsCannotProduceInvalidBoxes() {
        for (float value : new float[]{Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, 0, -1}) {
            assertThrows(IllegalArgumentException.class, () -> new RigDefinition.Hitbox(value, .75f, 0));
            assertThrows(IllegalArgumentException.class, () -> new RigDefinition.Hitbox(.5f, value, 0));
            var size = BodySize.resolve(FROG, value, value, BodySize.Posture.STANDING);
            assertEquals(.5f, size.width());
            assertEquals(.75f, size.height());
        }
        for (var pose : BodySize.Posture.values()) {
            var tiny = BodySize.resolve(FROG, 1e-30, 1e-30, pose);
            assertEquals(.2f, tiny.width()); assertEquals(.2f, tiny.height());
            assertTrue(tiny.eyeHeight() > 0 && tiny.eyeHeight() < tiny.height());
        }
    }
    @Test void fixedPolicyIgnoresScaleButStillClampsAndKeepsAnExplicitEyeHeight() {
        var base = new RigDefinition.Hitbox(.5f, .75f, .625f, .4f, .625f, false);
        var size = BodySize.resolve(base, 100, .1, BodySize.Posture.STANDING);
        assertEquals(.5f, size.width()); assertEquals(.75f, size.height());
        assertEquals(.625f, size.eyeHeight(), .00001);
    }
    @Test void deferredGrowthHoldsExpansionWithoutPreventingShrinkOrMovingTheFeet() {
        var size = new BodySize(.6f, 1.8f, 1.53f).deferGrowth(.5f, 1.0f);
        assertEquals(.5f, size.width()); assertEquals(1f, size.height());
        assertEquals(.85f, size.eyeHeight(), .00001);
        var shrinking = new BodySize(.4f, .75f, .6f).deferGrowth(.5f, 1);
        assertEquals(.4f, shrinking.width()); assertEquals(.75f, shrinking.height());
    }
}
