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
