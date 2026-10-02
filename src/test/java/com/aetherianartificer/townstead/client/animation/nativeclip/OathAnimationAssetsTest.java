package com.aetherianartificer.townstead.client.animation.nativeclip;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class OathAnimationAssetsTest {
    private BedrockPerformanceClip load(String name) throws Exception {
        try (var stream = getClass().getResourceAsStream(
                "/assets/townstead_performance/animations/townstead/" + name + ".animation.json")) {
            assertNotNull(stream, name);
            var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals("1.8.0", json.get("format_version").getAsString());
            var parsed = BedrockPerformanceClip.parse(json);
            assertEquals(Set.of("animation." + name), parsed.keySet());
            return parsed.get("animation." + name);
        }
    }

    @ParameterizedTest
    @CsvSource({"formal_stance,40", "kneel,30", "kneel_hold,80", "hunter_oath_swear,60",
            "hunter_oath_invoke,60", "hunter_oath_anoint,80", "rise_from_kneel,30", "fist_salute,30"})
    void runtimeLoadsAllEightClips(String name, float ticks) throws Exception {
        var clip = load(name);
        assertEquals(ticks, clip.durationTicks());
        assertEquals(name.equals("kneel_hold") ? BedrockPerformanceClip.Loop.LOOP : BedrockPerformanceClip.Loop.ONCE, clip.loop());
        for (float t = 0; t <= ticks; t += .25F) {
            for (var track : clip.bones().values()) {
                if (track.rotation() != null) for (float v : track.rotation().sample(t)) assertTrue(Float.isFinite(v));
                if (track.position() != null) for (float v : track.position().sample(t)) assertTrue(Float.isFinite(v));
            }
        }
    }

    private void matches(BedrockPerformanceClip a, float at, BedrockPerformanceClip b, float bt) {
        for (String bone : b.bones().keySet()) {
            var left = a.bones().get(bone);
            if (left == null) continue; // Compare the three oath overlay channels against a complete pose.
            var right = b.bones().get(bone);
            if (left.rotation() != null && right.rotation() != null)
                assertArrayEquals(left.rotation().sample(at), right.rotation().sample(bt), .0001F, bone);
            if (left.position() != null && right.position() != null)
                assertArrayEquals(left.position().sample(at), right.position().sample(bt), .0001F, bone);
        }
    }

    @ParameterizedTest
    @CsvSource({"formal_stance,kneel", "formal_stance,hunter_oath_invoke", "formal_stance,fist_salute",
            "kneel,kneel_hold", "kneel_hold,hunter_oath_swear", "hunter_oath_swear,rise_from_kneel",
            "hunter_oath_invoke,hunter_oath_anoint"})
    void adjacentClipsShareTheSameAuthoredPose(String first, String second) throws Exception {
        var a = load(first);
        matches(a, a.durationTicks(), load(second), 0);
    }

    @Test void returnsAndLoopSeamsAreStable() throws Exception {
        var hold = load("kneel_hold");
        matches(hold, 0, hold, 80);
        // Both sides of the seam settle to exactly the same pose and zero velocity.
        matches(hold, .01F, hold, 79.99F);
        var formal = load("formal_stance");
        matches(formal, 40, load("rise_from_kneel"), 30);
        matches(formal, 40, load("fist_salute"), 30);
        var anoint = load("hunter_oath_anoint");
        matches(anoint, 0, anoint, 80);
        assertArrayEquals(new float[]{0,-5,(float)Math.sqrt(35)}, hold.bones().get("root").position().sample(0), .0001F);
        for (float t = 0; t < 80; t += .25F)
            assertArrayEquals(hold.bones().get("root").position().sample(0), hold.bones().get("root").position().sample(t), .0001F);
    }

    @Test void swearLeavesTheKneelingBaseInControl() throws Exception {
        var swear = load("hunter_oath_swear");
        assertEquals(Set.of("head", "right_arm", "right_forearm"), swear.bones().keySet());
        for (var track : swear.bones().values()) assertNull(track.position());
        // Heart reached at .4s; face raised at .8s and held until 2.4s.
        assertArrayEquals(swear.bones().get("right_arm").rotation().sample(8), swear.bones().get("right_arm").rotation().sample(60));
        assertArrayEquals(swear.bones().get("head").rotation().sample(16), swear.bones().get("head").rotation().sample(48));
        assertTrue(swear.bones().get("head").rotation().sample(16)[0] < 0);
        assertTrue(swear.bones().get("head").rotation().sample(60)[0] > 0);
    }

    @Test void contactBeatsHaveDistinctSidesAndReturnTheRoot() throws Exception {
        var clip = load("hunter_oath_anoint");
        var arm = clip.bones().get("right_arm").rotation();
        assertTrue(arm.sample(24)[1] < 0);
        assertTrue(arm.sample(44)[1] > 0);
        assertEquals(-4F, clip.bones().get("root").position().sample(24)[2], .0001F);
        assertArrayEquals(new float[3], clip.bones().get("root").position().sample(80));
    }

    @Test void raisedFootStaysPlantedAndRestingShinClearsTheFloor() throws Exception {
        for (String name : new String[]{"kneel", "rise_from_kneel"}) {
            var clip = load(name);
            for (float t = 0; t <= clip.durationTicks(); t += .25F) {
                var root = clip.bones().get("root").position().sample(t);
                double thigh = Math.toRadians(clip.bones().get("left_leg").rotation().sample(t)[0]);
                double shin = thigh + Math.toRadians(clip.bones().get("left_shin").rotation().sample(t)[0]);
                assertEquals(0, 12 + root[1] - 6*Math.cos(thigh) - 6*Math.cos(shin), .035, name + " foot Y");
                assertEquals(0, root[2] + 6*Math.sin(thigh) + 6*Math.sin(shin), .035, name + " foot Z");
            }
        }
        var hold = load("kneel_hold");
        double thigh = hold.bones().get("right_leg").rotation().sample(0)[0];
        double shin = hold.bones().get("right_shin").rotation().sample(0)[0];
        assertTrue(thigh < 0, "The resting knee must be forward of its hip, not behind it");
        assertEquals(90, thigh + shin, .0001);
        assertEquals(0, 12 - 5 - 6*Math.cos(Math.toRadians(thigh)) - 2, .0001);
    }

    // Blockbench uses ZYX Euler order and reverses exported X/Y rotations.
    private double[] rotate(double[] v, float[] r) {
        double a = Math.toRadians(-r[0]), b = Math.toRadians(-r[1]), c = Math.toRadians(r[2]);
        double y = v[1]*Math.cos(a)-v[2]*Math.sin(a), z = v[1]*Math.sin(a)+v[2]*Math.cos(a);
        double x = v[0]*Math.cos(b)+z*Math.sin(b);
        z = -v[0]*Math.sin(b)+z*Math.cos(b);
        return new double[]{x*Math.cos(c)-y*Math.sin(c), x*Math.sin(c)+y*Math.cos(c), z};
    }

    @Test void invocationBladePointsUpAndArmReachesForwardAtTheBell() throws Exception {
        var clip = load("hunter_oath_invoke");
        var upper = clip.bones().get("right_arm").rotation().sample(32);
        var forearm = clip.bones().get("right_forearm").rotation().sample(32);
        var blade = rotate(rotate(new double[]{0,0,-1}, forearm), upper);
        assertTrue(blade[1] > .99, "Reference blade must point up at the bell");
        var elbow = rotate(new double[]{1,-4,0}, upper);
        var hand = rotate(rotate(new double[]{0,-5,0}, forearm), upper);
        assertTrue(elbow[2]+hand[2] < -8, "Arm must extend in front of the actor");
    }

    @Test void legCurvesDoNotPopBetweenAdjacentPreviewFrames() throws Exception {
        for (String name : new String[]{"kneel", "rise_from_kneel", "hunter_oath_anoint"}) {
            var clip = load(name);
            for (String bone : new String[]{"right_leg", "right_shin", "left_leg", "left_shin"}) {
                var track = clip.bones().get(bone).rotation();
                for (float t = .25F; t <= clip.durationTicks(); t += .25F) {
                    var previous = track.sample(t-.25F);
                    var current = track.sample(t);
                    for (int axis = 0; axis < 3; axis++)
                        assertTrue(Math.abs(current[axis]-previous[axis]) < 5,
                                name + " " + bone + " sudden knee change at tick " + t);
                }
            }
        }
    }
}
