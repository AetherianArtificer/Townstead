package com.aetherianartificer.townstead.root.attachment;

import com.aetherianartificer.townstead.client.attachment.geo.AttachmentGeo;
import com.aetherianartificer.townstead.client.attachment.geo.AttachmentGeoLoader;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HitShapesTest {

    private static final String BONES = "{\"name\":\"root\",\"pivot\":[0,10,0],\"rotation\":[10,20,30],"
            + "\"cubes\":[{\"origin\":[-2,8,-1],\"size\":[4,4,2]}]},"
            + "{\"name\":\"mid\",\"parent\":\"root\",\"pivot\":[1,12,3],\"rotation\":[-15,5,40],"
            + "\"cubes\":[{\"origin\":[0,10,3],\"size\":[2,6,8],\"inflate\":0.5,\"rotation\":[25,0,-10],\"pivot\":[1,13,4]}]},"
            + "{\"name\":\"empty\",\"parent\":\"mid\",\"pivot\":[1,15,11],\"rotation\":[0,30,0]},"
            + "{\"name\":\"tip\",\"parent\":\"empty\",\"pivot\":[2,15,12],"
            + "\"cubes\":[{\"origin\":[1,14,12],\"size\":[2,2,5]},{\"origin\":[1,16,12],\"size\":[1,1,1]}]}";

    private static JsonObject geo(String bones) {
        return JsonParser.parseString("{\"minecraft:geometry\":[{\"bones\":[" + bones + "]}]}").getAsJsonObject();
    }

    private static Matrix4f frame() {
        return new Matrix4f().translate(0.3f, 1.1f, -0.2f).rotateY(0.7f).rotateX(-0.4f).scale(0.8f);
    }

    /** The drawn bake's boxes, gathered by group the way a client measures them. */
    private static float[] drawn(AttachmentGeo bake, Map<String, Integer> groups, int count, Matrix4f frame) {
        float[] flat = new float[count * 6];
        for (int i = 0; i < flat.length; i += 6) {
            flat[i] = flat[i + 1] = flat[i + 2] = Float.MAX_VALUE;
            flat[i + 3] = flat[i + 4] = flat[i + 5] = -Float.MAX_VALUE;
        }
        bake.measure(frame, (bone, minX, minY, minZ, maxX, maxY, maxZ) -> {
            int at = HitShapes.groupOf(groups, bone) * 6;
            flat[at] = Math.min(flat[at], minX);
            flat[at + 1] = Math.min(flat[at + 1], minY);
            flat[at + 2] = Math.min(flat[at + 2], minZ);
            flat[at + 3] = Math.max(flat[at + 3], maxX);
            flat[at + 4] = Math.max(flat[at + 4], maxY);
            flat[at + 5] = Math.max(flat[at + 5], maxZ);
        });
        return flat;
    }

    private static void assertSame(float[] expected, float[] actual) {
        assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) assertEquals(expected[i], actual[i], 1e-4f, "value " + i);
    }

    /** What the server works out has to be what the render bake draws, or every hit is a guess. */
    @Test
    void theWorkedOutPoseMatchesTheDrawnBake() {
        JsonObject json = geo(BONES);
        var groups = HitShapes.groups(json);
        BodySkeleton skeleton = BodySkeleton.parse(json, groups);
        AttachmentGeo bake = AttachmentGeoLoader.parse(json);
        assertSame(drawn(bake, groups, skeleton.groups, frame()),
                BodyPose.solve(skeleton, frame(), BodyPose.Posed.NONE, List.of(), false));
    }

    @Test
    void itMatchesAMirroredBakeToo() {
        JsonObject json = geo(BONES);
        var groups = HitShapes.groups(json);
        BodySkeleton skeleton = BodySkeleton.parse(json, groups);
        AttachmentGeo bake = AttachmentGeoLoader.parse(json).mirrored();
        assertSame(drawn(bake, groups, skeleton.groups, frame()),
                BodyPose.solve(skeleton, frame(), BodyPose.Posed.NONE, List.of(), true));
    }

    /** Pose rotations, keyframe offsets and scales go on as the render layer puts them on its bake. */
    @Test
    void itMatchesThePosedBake() {
        JsonObject json = geo(BONES);
        var groups = HitShapes.groups(json);
        BodySkeleton skeleton = BodySkeleton.parse(json, groups);
        AttachmentGeo bake = AttachmentGeoLoader.parse(json);
        AttachmentGeo.Bone mid = bake.bone("mid");
        float[] rotation = {12f, 34f, -56f}, position = {1f, 2f, -3f}, scale = {1.2f, 0.7f, 1.5f};
        mid.xRot += (float) Math.toRadians(rotation[0]);
        mid.yRot += (float) Math.toRadians(-rotation[1]);
        mid.zRot += (float) Math.toRadians(-rotation[2]);
        mid.xOff += position[0];
        mid.yOff += -position[1];
        mid.zOff += position[2];
        mid.xScale = scale[0];
        mid.yScale = scale[1];
        mid.zScale = scale[2];
        BodyPose.Posed posed = new BodyPose.Posed(Map.of("mid", rotation), Map.of("mid", position), Map.of("mid", scale), List.of());
        assertSame(drawn(bake, groups, skeleton.groups, frame()),
                BodyPose.solve(skeleton, frame(), posed, List.of(), false));
    }

    @Test
    void hiddenBonesAndTheirChildrenLeaveNothing() {
        JsonObject json = geo(BONES);
        var groups = HitShapes.groups(json);
        BodySkeleton skeleton = BodySkeleton.parse(json, groups);
        float[] flat = BodyPose.solve(skeleton, frame(), new BodyPose.Posed(Map.of(), Map.of(), Map.of(), List.of("mid")), List.of(), false);
        assertFalse(flat[0] > flat[3], "the root still shows");
        assertTrue(flat[6] > flat[9], "the hidden bone measures inside out");
        assertTrue(flat[12] > flat[15], "and so does its child");
    }

    private static AttachmentDef.PhysicsChain chain(List<String> bones, float gravity, float limit) {
        AttachmentDef.Knob zero = AttachmentDef.Knob.of(0f);
        return new AttachmentDef.PhysicsChain(bones, "", AttachmentDef.Knob.of(2f), AttachmentDef.Knob.of(0.7f),
                AttachmentDef.Knob.of(gravity), AttachmentDef.Knob.of(1f), zero, AttachmentDef.Knob.of(limit), "none",
                List.of(), 0f, 0f, 1f, false, 1, "auto", zero, true, -1f, List.of());
    }

    /** A straight tail out behind, in a frame with y up. */
    private static final String TAIL = "{\"name\":\"a\",\"pivot\":[0,0,0],\"cubes\":[{\"origin\":[-1,-1,0],\"size\":[2,2,8]}]},"
            + "{\"name\":\"b\",\"parent\":\"a\",\"pivot\":[0,0,8],\"cubes\":[{\"origin\":[-1,-1,8],\"size\":[2,2,8]}]}";

    private static float lowestY(float gravity, float limit) {
        JsonObject json = geo(TAIL);
        BodySkeleton skeleton = BodySkeleton.parse(json, HitShapes.groups(json));
        List<AttachmentDef.PhysicsChain> chains = gravity < 0f ? List.of() : List.of(chain(List.of("a", "b"), gravity, limit));
        float[] flat = BodyPose.solve(skeleton, new Matrix4f(), BodyPose.Posed.NONE, chains, false);
        return Math.min(flat[1], flat[7]);
    }

    @Test
    void aChainDroopsUnderGravityAndStopsAtItsLimit() {
        float still = lowestY(-1f, 45f);
        float weightless = lowestY(0f, 45f);
        float heavy = lowestY(1f, 45f);
        float held = lowestY(1f, 10f);
        assertEquals(still, weightless, 1e-3f, "no gravity and no pose change means it stays where it was authored");
        assertTrue(heavy < still - 0.1f, "gravity pulls it down: " + heavy + " vs " + still);
        assertTrue(held > heavy + 0.05f, "a tight limit holds it up: " + held + " vs " + heavy);
    }

    @Test
    void aChainLiesOnAFloorInsteadOfPassingThrough() {
        JsonObject json = geo(TAIL);
        BodySkeleton skeleton = BodySkeleton.parse(json, HitShapes.groups(json));
        float floor = -0.2f;
        BodyPose.Blocks blocks = (head, tail, n, into) -> {
            float[] box = {-4f, -2f, -4f, 4f, floor, 4f};
            System.arraycopy(box, 0, into, 0, 6);
            return 1;
        };
        List<AttachmentDef.PhysicsChain> chains = List.of(chain(List.of("a", "b"), 1f, 80f));
        float[] free = BodyPose.solve(skeleton, new Matrix4f(), BodyPose.Posed.NONE, chains, false, null, null);
        float[] lying = BodyPose.solve(skeleton, new Matrix4f(), BodyPose.Posed.NONE, chains, false, null, blocks);
        assertTrue(Math.min(free[1], free[7]) < floor - 0.1f, "with no floor it hangs below where the floor would be");
        assertTrue(Math.min(lying[1], lying[7]) > floor - 0.2f, "with one it stays up: " + Math.min(lying[1], lying[7]));
    }

    private static AttachmentDef.AnimationEntry idle(String clip, float weight, int cooldown) {
        return new AttachmentDef.AnimationEntry("", "", "sha", clip, 2f, weight, cooldown, true);
    }

    private static AttachmentAnimation.Clip idleClip() {
        JsonObject file = JsonParser.parseString("{\"animations\":{\"a\":{\"animation_length\":1,\"bones\":{\"x\":{\"rotation\":[1,0,0]}}}}}").getAsJsonObject();
        return AttachmentAnimation.parse(file, null).values().iterator().next();
    }

    @Test
    void idlesAreTheSameForEveryoneAndRespectCooldowns() {
        List<AttachmentDef.AnimationEntry> entries = List.of(idle("a", 1f, 400), idle("b", 1f, 0));
        AttachmentAnimation.Clip clip = idleClip();
        int played = 0;
        long lastA = Long.MIN_VALUE;
        long shortestGap = Long.MAX_VALUE;
        for (long tick = 0; tick < 20_000; tick++) {
            var one = BodyPoseRules.idlePick(42L, entries, (sha, name) -> clip, tick);
            var two = BodyPoseRules.idlePick(42L, entries, (sha, name) -> clip, tick);
            assertEquals(one, two, "same seed and clock, same answer");
            if (one == null) continue;
            played++;
            if (one.entry() == 0 && one.start() != lastA) {
                if (lastA != Long.MIN_VALUE) shortestGap = Math.min(shortestGap, one.start() - lastA);
                lastA = one.start();
            }
        }
        assertTrue(played > 0, "idles do play");
        assertTrue(shortestGap >= 320, "a cooldown keeps an entry apart from itself: " + shortestGap);
    }

    @Test
    void differentPeopleGetDifferentIdles() {
        List<AttachmentDef.AnimationEntry> entries = List.of(idle("a", 1f, 0));
        AttachmentAnimation.Clip clip = idleClip();
        boolean differ = false;
        for (long tick = 0; tick < 2_000 && !differ; tick++) {
            differ = !java.util.Objects.equals(BodyPoseRules.idlePick(1L, entries, (sha, name) -> clip, tick),
                    BodyPoseRules.idlePick(2L, entries, (sha, name) -> clip, tick));
        }
        assertTrue(differ);
    }

    @Test
    void groupsAreStableAndCappedForBothSides() {
        StringBuilder bones = new StringBuilder("{\"name\":\"empty\",\"pivot\":[0,0,0]}");
        for (int i = 0; i < 20; i++) {
            bones.append(",{\"name\":\"b").append(i).append("\",\"pivot\":[0,0,0],\"cubes\":[{\"origin\":[0,0,0],\"size\":[1,1,1]}]}");
        }
        var groups = HitShapes.groups(geo(bones.toString()));
        assertEquals(20, groups.size());
        assertEquals(0, groups.get("b0"));
        assertEquals(7, groups.get("b19"));
        assertFalse(groups.containsKey("empty"));
        assertEquals(7, HitShapes.groupOf(groups, "b19__seg3"), "a slice stays with the bone it was cut from");
    }
}
