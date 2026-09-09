package com.aetherianartificer.townstead.client.animation.nativeclip;

import com.aetherianartificer.townstead.client.animation.AnimationTargetMap;
import com.google.gson.JsonParser;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class BedrockPerformanceSamplerTest {
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"shiver", "sweat", "cry", "laugh", "laugh_demure"})
    void mobileReactionsReleaseLegsButKeepUpperBody(String name) throws Exception {
        try (var stream = getClass().getResourceAsStream(
                "/assets/townstead_performance/animations/townstead/" + name + ".animation.json")) {
            assertNotNull(stream);
            var clip = BedrockPerformanceClip.parse(JsonParser.parseReader(
                    new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject())
                    .get("animation." + name);
            var id = net.minecraft.resources.ResourceLocation.tryParse("townstead_performance:" + name);
            var targets = targets();
            targets.resolve("left_leg").orElseThrow().xRot = .6F;
            targets.resolve("right_leg").orElseThrow().xRot = -.6F;
            var standing = BedrockPerformanceSampler.sample(clip, 16, 100, targets,
                    NativeLocomotionPolicy.lowerBodyWeight(id, 0));
            var walking = BedrockPerformanceSampler.sample(clip, 16, 100, targets,
                    NativeLocomotionPolicy.lowerBodyWeight(id, .2F));
            assertFalse(walking.isEmpty());
            assertTrue(walking.stream().noneMatch(t -> t.target().endsWith("_leg")));
            assertEquals(standing.stream().filter(t -> !t.target().endsWith("_leg")).toList(), walking);
            assertEquals(.6F, targets.resolve("left_leg").orElseThrow().xRot);
            if (!name.equals("sweat")) {
                assertTrue(standing.stream().anyMatch(t -> t.target().endsWith("_leg") && t.applyBend()));
                var transitioning = BedrockPerformanceSampler.sample(clip, 16, 100, targets,
                        NativeLocomotionPolicy.lowerBodyWeight(id, .05F));
                var standingLeg = standing.stream().filter(t -> t.target().equals("left_leg") && t.xRot() != null).findFirst().orElseThrow();
                var blendedLeg = transitioning.stream().filter(t -> t.target().equals("left_leg") && t.xRot() != null).findFirst().orElseThrow();
                assertEquals((.6F + standingLeg.xRot()) / 2F, blendedLeg.xRot(), .0001F);
            }
        }
    }

    @Test void locomotionPolicyPreservesIntentionalFullBodyAndExternalClips() {
        for (String name : List.of("stool_sit", "recline", "recline_lounger", "relaxed_lean", "tap_foot", "startled", "cheer_excited")) {
            assertEquals(1F, NativeLocomotionPolicy.lowerBodyWeight(
                    net.minecraft.resources.ResourceLocation.tryParse("townstead_performance:" + name), 1F));
        }
        assertEquals(1F, NativeLocomotionPolicy.lowerBodyWeight(
                net.minecraft.resources.ResourceLocation.tryParse("custom:shiver"), 1F));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "shiver,16,right,35,65,-8,76,1",
            "shiver,16,left,43,-65,8,82,-1",
            "toast,10.5,right,0,-90,90,80,1"
    })
    void approvedBlockbenchHandDirectionSurvivesRetargeting(String name, float tick, String side,
            float x, float y, float z, float elbow, float elbowOffsetX) throws Exception {
        // Reference poses from the approved Blockbench timelines. Compare the actual hand
        // endpoint through shoulder + elbow, not merely the signs copied into a transform.
        var radians = (float) (Math.PI / 180);
        var expected = new org.joml.Vector3f(0, -4, 0).rotateX(elbow * radians)
                .add(elbowOffsetX, -4, 0)
                .mul(new org.joml.Matrix3f().rotationZYX(z * radians, y * radians, x * radians))
                .mul(-1, -1, 1); // Blockbench's +X/+Y become MCA's -X/-Y.
        try (var stream = getClass().getResourceAsStream(
                "/assets/townstead_performance/animations/townstead/" + name + ".animation.json")) {
            assertNotNull(stream);
            var exported = BedrockPerformanceClip.parse(JsonParser.parseReader(
                    new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject())
                    .get("animation." + name);
            var transforms = BedrockPerformanceSampler.sample(exported, tick, 100, targets());
            var shoulder = transforms.stream().filter(t -> t.target().equals(side + "_arm") && t.xRot() != null)
                    .findFirst().orElseThrow();
            var hinge = transforms.stream().filter(t -> t.target().equals(side + "_arm") && t.applyBend())
                    .findFirst().orElseThrow();
            var actual = new org.joml.Vector3f(0, 4, 0).rotateX(hinge.bend())
                    .add(-elbowOffsetX, 4, 0)
                    .mul(new org.joml.Matrix3f().rotationZYX(shoulder.zRot(), shoulder.yRot(), shoulder.xRot()));
            assertEquals(expected.x, actual.x, .001, "hand X: inward/outward");
            assertEquals(expected.y, actual.y, .001, "hand Y: raised/lowered");
            assertEquals(expected.z, actual.z, .001, "hand Z: forward/backward");
        }
    }
    @Test void rootTranslationMovesTheWholeRenderInBlocksAndRespectsSeatingAndLifetime() {
        var hop = BedrockPerformanceClip.parse(JsonParser.parseString("""
                {"format_version":"1.8.0","animations":{"animation.hop":{
                "animation_length":2,"bones":{"root":{"position":{
                "0":[0,0,0],"0.5":[2,4,1],"0.75":[0,0,0],"2":[0,0,0]}}}}}}
                """).getAsJsonObject()).get("animation.hop");
        assertArrayEquals(new float[]{-.125F,.25F,.0625F},
                BedrockPerformanceSampler.rootTranslation(hop,10,30,false), .0001F);
        assertArrayEquals(new float[3], BedrockPerformanceSampler.rootTranslation(hop,10,30,true));
        assertArrayEquals(new float[3], BedrockPerformanceSampler.rootTranslation(hop,15,25,false), .0001F);
        assertArrayEquals(new float[3], BedrockPerformanceSampler.rootTranslation(hop,40,20,false));
        assertArrayEquals(new float[3], BedrockPerformanceSampler.rootTranslation(hop,10,0,false));
        assertTrue(BedrockPerformanceSampler.sample(hop,10,30,targets()).isEmpty());
    }

    @Test void rejectsUnsupportedRootRotation() {
        assertThrows(IllegalArgumentException.class, () -> BedrockPerformanceClip.parse(JsonParser.parseString("""
                {"format_version":"1.8.0","animations":{"animation.bad":{
                "animation_length":1,"bones":{"root":{"rotation":[10,0,0]}}}}}
                """).getAsJsonObject()));
    }

    private AnimationTargetMap<?> targets() {
        var parts = new HashMap<String, ModelPart>();
        for (String name : List.of("head", "hat", "body", "right_arm", "left_arm", "right_leg", "left_leg"))
            parts.put(name, new ModelPart(List.of(), java.util.Map.of()));
        parts.get("right_arm").setPos(-5, 2, 0);
        return AnimationTargetMap.forMcaModel(new HumanoidModel<>(new ModelPart(List.of(), parts)));
    }
    private BedrockPerformanceClip clip(String loop) {
        return BedrockPerformanceClip.parse(JsonParser.parseString("""
            {"format_version":"1.8.0","animations":{"animation.test":{
             "animation_length":1,"loop":%s,"bones":{
             "right_arm":{"position":[1,2,3]},"head":{"rotation":[10,20,30]},
             "right_forearm":{"rotation":[-25,0,0]}}}}}
            """.formatted(loop)).getAsJsonObject()).get("animation.test");
    }
    @Test void mapsAxesOffsetsAndHingeWithoutClaimingUnauthoredRotation() {
        var result = BedrockPerformanceSampler.sample(clip("false"), 8, 12, targets());
        var arm = result.stream().filter(t -> t.target().equals("right_arm") && t.applyTranslation()).findFirst().orElseThrow();
        assertEquals(-4F, arm.x()); assertEquals(0F, arm.y()); assertEquals(3F, arm.z());
        assertNull(arm.xRot());
        var head = result.stream().filter(t -> t.target().equals("head")).findFirst().orElseThrow();
        assertEquals(Math.toRadians(10), head.xRot(), .0001);
        assertEquals(Math.toRadians(20), head.yRot(), .0001);
        assertEquals(Math.toRadians(30), head.zRot(), .0001);
        var hinge = result.stream().filter(t -> t.applyBend()).findFirst().orElseThrow();
        assertEquals(Math.toRadians(-25), hinge.bend(), .0001);
    }
    @Test void oneShotsReleaseWhileHoldAndLoopRespectPerformanceLifetime() {
        assertTrue(BedrockPerformanceSampler.sample(clip("false"), 21, 20, targets()).isEmpty());
        assertFalse(BedrockPerformanceSampler.sample(clip("true"), 21, 20, targets()).isEmpty());
        assertFalse(BedrockPerformanceSampler.sample(clip("\"hold_on_last_frame\""), 21, 20, targets()).isEmpty());
        assertTrue(BedrockPerformanceSampler.sample(clip("true"), 21, 0, targets()).isEmpty());
        assertTrue(BedrockPerformanceSampler.sample(clip("false"), 0, 20, targets()).isEmpty());
    }
}
