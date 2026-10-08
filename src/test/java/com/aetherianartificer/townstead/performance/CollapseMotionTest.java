package com.aetherianartificer.townstead.performance;

import com.aetherianartificer.townstead.client.animation.nativeclip.BedrockPerformanceClip;
import com.google.gson.JsonParser;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class CollapseMotionTest {
    @Test void trackingSyncPreservesTheOriginalAnimationClock() {
        var packet = new NativePerformanceS2CPayload(12, CollapseMotion.CHANNEL, CollapseMotion.CLIP, 600, 1000, 12345);
        var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            //? if neoforge {
            NativePerformanceS2CPayload.STREAM_CODEC.encode(buffer, packet);
            assertEquals(packet, NativePerformanceS2CPayload.STREAM_CODEC.decode(buffer));
            //?} else {
            /*packet.write(buffer);
            assertEquals(packet, NativePerformanceS2CPayload.read(buffer));
            *///?}
            assertEquals(-1, new NativePerformanceS2CPayload(12, "reaction", "test:wave", 32, 30).startedAt());
        } finally { buffer.release(); }
    }
    @Test void movementFollowsFacingAndEndsWithoutSnappingBack() {
        var south = CollapseMotion.step(0, 104, 0);
        assertTrue(south.z > 1.5 && south.z < 1.8, "Landing center should advance about 1.65 blocks");
        var west = CollapseMotion.step(0, 104, 90);
        assertEquals(-south.z, west.x, .00001);
        assertEquals(0, south.y);
        assertEquals(CollapseMotion.position(104), CollapseMotion.position(1200));
        assertEquals(0, CollapseMotion.step(104, 105, 0).lengthSqr());
    }

    @Test void blockedStepDoesNotBecomePartOfTheNextStep() {
        var expected = CollapseMotion.position(18).subtract(CollapseMotion.position(17));
        // A collision discards tick 16->17; the next tick still uses 17->18.
        var next = CollapseMotion.step(17, 18, 180);
        assertEquals(expected.x, next.x, .000001);
        assertEquals(expected.z, next.z, .000001);
        assertTrue(next.length() < .1);
    }

    @Test void exportedRootRotationFallsForwardAndCentersTheBodyAtItsRealPosition() {
        float[] exported = {90, 0, 0}; // Blockbench display X=-90.
        var midpoint = new Vector3f(0, 1, 0).rotate(CollapseMotion.rotation(exported));
        assertEquals(-1, midpoint.z, .00001);
        assertEquals(0, midpoint.y, .00001);
        var compensation = CollapseMotion.anchor(exported).scale(-1);
        assertEquals(0, midpoint.x + compensation.x, .00001);
        assertEquals(0, midpoint.z + compensation.z, .00001);
    }

    @Test void lieDownSettlesWithoutLateReversalsAndHoldsStill() throws Exception {
        try (var stream = getClass().getResourceAsStream(
                "/assets/townstead_performance/animations/townstead/fatigue.animation.json")) {
            assertNotNull(stream);
            var clip = BedrockPerformanceClip.parse(JsonParser.parseReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject())
                    .get("animation.fatigue_lie_down");
            assertEquals(BedrockPerformanceClip.Loop.HOLD, clip.loop());
            // The hands release at 3.3 seconds. They must not retract and reach
            // again while the torso is settling (the previous export twitched).
            for (String bone : java.util.List.of("right_arm", "left_arm", "right_forearm", "left_forearm")) {
                var rotation = clip.bones().get(bone).rotation();
                float previous = rotation.sample(66)[0];
                float direction = Math.signum(rotation.sample(clip.durationTicks())[0] - previous);
                for (float tick = 66.125F; tick <= clip.durationTicks(); tick += .125F) {
                    float current = rotation.sample(tick)[0];
                    assertTrue((current - previous) * direction >= -.0001F, bone + " reverses during settling");
                    assertTrue(Math.abs(current - previous) <= .6F, bone + " snaps during settling");
                    previous = current;
                }
            }
            var root = clip.bones().get("root");
            double previousHipHeight = Double.POSITIVE_INFINITY;
            double previousChestHeight = Double.POSITIVE_INFINITY;
            // Cover the earlier kneeling transition too: the backwards thigh
            // excursion previously lifted the hips and chest around 3 seconds.
            for (float tick = 43.5F; tick <= clip.durationTicks(); tick += .125F) {
                double hipHeight = root.position().sample(tick)[1]
                        + 12 * Math.cos(Math.toRadians(root.rotation().sample(tick)[0]));
                // Linear interpolation between baked Euler keys introduces a
                // subpixel arc error; compare against the lowest height seen
                // so this tolerance cannot hide an accumulating rebound.
                assertTrue(hipHeight <= previousHipHeight + .005, "Body rises again during settling");
                double chestHeight = hipHeight + 6 * Math.cos(Math.toRadians(
                        root.rotation().sample(tick)[0] + clip.bones().get("body").rotation().sample(tick)[0]));
                assertTrue(chestHeight <= previousChestHeight + .0001, "Torso drifts upward during descent");
                previousHipHeight = Math.min(previousHipHeight, hipHeight);
                previousChestHeight = chestHeight;
            }
            for (var track : clip.bones().values()) {
                for (var channel : new BedrockPerformanceClip.Channel[]{track.rotation(), track.position()}) {
                    if (channel == null) continue;
                    var held = channel.sample(clip.durationTicks());
                    for (float tick = 106; tick <= clip.durationTicks() + 20; tick += .125F)
                        assertArrayEquals(held, channel.sample(tick), .0001F);
                }
            }
        }
    }

    @Test void fatigueExportsLoadAndYawnReturnsToTheWalkingCompatibleTiredPose() throws Exception {
        try (var stream = getClass().getResourceAsStream(
                "/assets/townstead_performance/animations/townstead/fatigue.animation.json")) {
            assertNotNull(stream);
            var clips = BedrockPerformanceClip.parse(JsonParser.parseReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject());
            var tired = clips.get("animation.fatigue_tired");
            var yawn = clips.get("animation.fatigue_yawn");
            var fall = clips.get("animation.fatigue_pass_out");
            assertEquals(BedrockPerformanceClip.Loop.HOLD, fall.loop());
            assertEquals(CollapseMotion.DURATION, fall.durationTicks());
            for (var entry : tired.bones().entrySet()) {
                assertFalse(entry.getKey().contains("leg") || entry.getKey().contains("shin") || entry.getKey().equals("root"));
                var other = yawn.bones().get(entry.getKey());
                var track = entry.getValue();
                if (track.rotation() != null) {
                    assertArrayEquals(track.rotation().sample(0), other.rotation().sample(yawn.durationTicks()), .0001F);
                    assertArrayEquals(track.rotation().sample(0), track.rotation().sample(tired.durationTicks()), .0001F);
                }
                if (track.position() != null)
                    assertArrayEquals(track.position().sample(0), other.position().sample(yawn.durationTicks()), .0001F);
            }
        }
    }
}
