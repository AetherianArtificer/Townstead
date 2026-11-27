package com.aetherianartificer.townstead.root.attachment;

import com.aetherianartificer.townstead.root.CanonicalStage;
import com.aetherianartificer.townstead.root.LifeStage;
import com.aetherianartificer.townstead.root.LifeStageProgression;
import com.aetherianartificer.townstead.root.gene.Allele;
import com.aetherianartificer.townstead.root.gene.AllelePayload;
import com.aetherianartificer.townstead.root.rig.RigDefinition;
import com.aetherianartificer.townstead.root.rig.RigHitboxes;
import com.aetherianartificer.townstead.root.rig.ServerRig;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Where a worn attachment's bones are, worked out from data alone: the model files, the bearer's stage, rig
 * and state, the world clock and the blocks around it. The same code runs on both sides, and on the server
 * its answer decides what gets hit. It follows the steps the render layer takes, down to the sign flips, so
 * what it works out is what gets drawn. Physics chains sit where their spring and gravity settle against
 * the blocks around them, unless a projectile has woken the body ({@link BodyPoseSim}) and they're
 * swinging. Emotes aren't in it, and neither are rigs built on another mod's vanilla-style model classes.
 */
public final class BodyPose {

    /** What's different on a client: where its copies of things are. */
    public interface ClientSide {
        @Nullable JsonObject geometry(String sha);
        @Nullable JsonObject rigGeometry(String ref);
        @Nullable Map<String, AttachmentAnimation.Clip> rigAnimations(String ref);
        List<AttachmentPointDef> points();
        String rigId(LivingEntity entity);
        @Nullable RigDefinition rig(String id);
        AllelePayload rolled(LivingEntity entity, AttachmentDef def);
        /** The stage's own name, then what it presents as. */
        String[] stage(LivingEntity entity);
        @Nullable AttachmentAnimation.Clip clip(String sha, String name);
    }

    /** The solid boxes around a chain, relative to the feet, into six floats a box; how many there are. */
    @FunctionalInterface
    interface Blocks {
        int gather(Vector3f head, Vector3f[] tail, int n, float[] into);
    }

    // Share of the parent segment's swing each child inherits in its aim. The physics sim uses this too.
    public static final float CARRY = 0.25f;
    private static final Vector3f DOWN = new Vector3f(0f, -1f, 0f);
    private static final float PLAYER_MODEL_SCALE = 0.9375f;
    private static final float MODEL_FEET = 1.501f;
    // Chain thickness for collision, in model pixels: bounds on what's measured from the geometry, and the
    // value for a bone with none to measure.
    private static final float RADIUS_MIN = 0.5f;
    private static final float RADIUS_MAX = 8f;
    private static final float RADIUS_FALLBACK = 1.5f;
    private static final int SETTLE_PASSES = 3;
    private static final float GAIT_BOB = 0.03f;

    // Bone pivots of vanilla models the server can't load, by the rig's model reference.
    private static final Map<String, Map<String, float[]>> VANILLA_PIVOTS = Map.of("minecraft:spider", Map.ofEntries(
            Map.entry("head", new float[]{0f, 15f, -3f}), Map.entry("body0", new float[]{0f, 15f, 0f}),
            Map.entry("body1", new float[]{0f, 15f, 9f}), Map.entry("right_hind_leg", new float[]{-4f, 15f, 2f}),
            Map.entry("left_hind_leg", new float[]{4f, 15f, 2f}), Map.entry("right_middle_hind_leg", new float[]{-4f, 15f, 1f}),
            Map.entry("left_middle_hind_leg", new float[]{4f, 15f, 1f}), Map.entry("right_middle_front_leg", new float[]{-4f, 15f, 0f}),
            Map.entry("left_middle_front_leg", new float[]{4f, 15f, 0f}), Map.entry("right_front_leg", new float[]{-4f, 15f, -1f}),
            Map.entry("left_front_leg", new float[]{4f, 15f, -1f})));

    private static final Map<String, BodySkeleton> SKELETONS = new ConcurrentHashMap<>();
    private static final Map<String, Map<String, AttachmentAnimation.Clip>> CLIPS = new ConcurrentHashMap<>();
    private static final Map<String, Optional<JsonObject>> RIG_GEOMETRY = new ConcurrentHashMap<>();
    private static final Map<String, Map<String, AttachmentAnimation.Clip>> RIG_ANIMATIONS = new ConcurrentHashMap<>();
    private static final float BABY_SEAT = 0.6f;

    private static volatile ClientSide client;

    private BodyPose() {}

    public static void setClient(ClientSide side) {
        client = side;
    }

    /** Forgets what was read by name from data or jars. Files found by content hash can't go stale. */
    static void onReload() {
        RIG_GEOMETRY.clear();
        RIG_ANIMATIONS.clear();
    }

    /** What a pose asks of a model's bones this moment, on top of how it was authored. */
    record Posed(Map<String, float[]> rotations, Map<String, float[]> positions, Map<String, float[]> scales,
                 List<String> hidden) {
        static final Posed NONE = new Posed(Map.of(), Map.of(), Map.of(), List.of());
    }

    private record Anchor(String bone, float[] offset, float[] rotation, boolean mirror) {}

    /** One attachment on one body this moment, before any of its anchors. */
    private record Setup(AttachmentDef def, BodySkeleton skeleton, @Nullable RigDefinition rig, List<Anchor> anchors,
                         Posed posed, @Nullable float[] whole, @Nullable float[] wholeScale, float stageScale,
                         float[] stageOffset, Matrix4f root) {}

    // --- From an entity -----------------------------------------------------------------------

    @Nullable
    private static Setup setup(LivingEntity entity, AttachmentDef def) {
        if (!HitShapes.active(entity, def)) return null;
        AttachmentDef.StageOverride stage = stageOf(entity, def);
        BodySkeleton skeleton = skeletonFor(def, stage, entity.level().isClientSide);
        if (skeleton == null || skeleton.groups == 0) return null;
        RigDefinition rig = rigOf(entity);
        BodyPoseRules.Morphs morphs = BodyPoseRules.morphs(entity, def, rolled(entity, def));
        return new Setup(def, skeleton, rig, anchors(entity, def, rig), withMorphs(posed(entity, def), morphs),
                BodyPoseRules.poseRotation(entity, def), morphs.whole(), stage == null ? 1f : stage.scale(),
                stage == null ? new float[3] : stage.offset(), root(entity));
    }

    @Nullable
    private static Matrix4f frame(Setup setup, LivingEntity entity, Anchor anchor) {
        Matrix4f frame = wornFrame(setup.root(), entity, setup.rig(), anchor.bone());
        if (frame == null) return null;
        AttachmentDef def = setup.def();
        float stageScale = setup.stageScale();
        float[] stageOffset = setup.stageOffset();
        frame.translate((anchor.offset()[0] + def.offset()[0] * stageScale + stageOffset[0]) / 16f,
                (anchor.offset()[1] + def.offset()[1] * stageScale + stageOffset[1]) / 16f,
                (anchor.offset()[2] + def.offset()[2] * stageScale + stageOffset[2]) / 16f);
        frame.rotate(degrees(anchor.rotation()));
        float[] r = def.rotation();
        frame.rotate(degrees(anchor.mirror() ? new float[]{r[0], -r[1], -r[2]} : r));
        float[] whole = setup.whole();
        if (whole != null) frame.rotate(degrees(anchor.mirror() ? new float[]{whole[0], -whole[1], -whole[2]} : whole));
        frame.scale(def.scale() * stageScale);
        if (setup.wholeScale() != null) frame.scale(setup.wholeScale()[0], setup.wholeScale()[1], setup.wholeScale()[2]);
        return frame;
    }

    /**
     * Where a blow landed on a body. {@code world} is where it is now. {@code limb} and {@code local} stay
     * with the body as it moves. {@code modelPoint} and {@code modelDirection} are on the body at rest, in
     * pixels with y up, as a Trace's surface has it. {@code exact} is false for a graze nearby, or for a
     * point with no limb to go with it.
     */
    public record Strike(Vec3 world, String limb, Vector3f local, Vector3f modelPoint, Vector3f modelDirection, boolean exact) {}

    // A shot counts a little past the body, so a graze still draws from the body.
    private static final double GRAZE = 0.3;

    private record Limb(String name, Matrix4f frame, Matrix4f rest, float[] box) {}

    // A humanoid's limbs, in pixels from each bone's pivot with y down, then the pivot at rest.
    private static final Map<String, float[][]> HUMANOID_LIMBS = Map.of(
            "head", new float[][]{{-4f, -8f, -4f, 4f, 0f, 4f}, {0f, 0f, 0f}},
            "body", new float[][]{{-4f, 0f, -2f, 4f, 12f, 2f}, {0f, 0f, 0f}},
            "right_arm", new float[][]{{-3f, -2f, -2f, 1f, 10f, 2f}, {-5f, 2f, 0f}},
            "left_arm", new float[][]{{-1f, -2f, -2f, 3f, 10f, 2f}, {5f, 2f, 0f}},
            "right_leg", new float[][]{{-2f, 0f, -2f, 2f, 12f, 2f}, {-1.9f, 12f, 0.1f}},
            "left_leg", new float[][]{{-2f, 0f, -2f, 2f, 12f, 2f}, {1.9f, 12f, 0.1f}});

    private static List<Limb> limbs(LivingEntity entity) {
        Matrix4f root = root(entity);
        RigDefinition rig = rigOf(entity);
        List<Limb> out = new ArrayList<>();
        if (rig == null || !generic(rig)) {
            HUMANOID_LIMBS.forEach((name, data) -> {
                Matrix4f frame = humanoidFrame(root, entity, name);
                if (frame == null) return;
                float[] box = new float[6];
                for (int i = 0; i < 6; i++) box[i] = data[0][i] / 16f;
                out.add(new Limb(name, frame, new Matrix4f().translate(data[1][0] / 16f, data[1][1] / 16f, data[1][2] / 16f), box));
            });
            return out;
        }
        if (rig.modelType() != RigDefinition.ModelType.GEOMETRY) return out;
        JsonObject geometry = rigGeometry(rig.modelRef(), entity.level().isClientSide);
        var bones = geometry == null ? null : HitShapes.bonesOf(geometry);
        if (bones == null) return out;
        for (var element : bones) {
            JsonObject bone = element.getAsJsonObject();
            float[] box = cubeBox(bone);
            if (box == null) continue;
            String name = bone.get("name").getAsString();
            Matrix4f frame = rigBoneFrame(root, entity, rig, name, true);
            Matrix4f rest = rigBoneFrame(new Matrix4f(), entity, rig, name, false);
            if (frame != null && rest != null) out.add(new Limb(name, frame, rest, box));
        }
        return out;
    }

    /** All of a bone's cubes as one box from its pivot, in blocks with y down, or null when it has none. */
    @Nullable
    private static float[] cubeBox(JsonObject bone) {
        var cubes = bone.getAsJsonArray("cubes");
        if (cubes == null || cubes.isEmpty()) return null;
        float[] pivot = BodySkeleton.vec(bone, "pivot");
        float[] box = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (var element : cubes) {
            JsonObject cube = element.getAsJsonObject();
            float[] origin = BodySkeleton.vec(cube, "origin"), size = BodySkeleton.vec(cube, "size");
            float x = origin[0] - pivot[0], y = -(origin[1] - pivot[1]) - size[1], z = origin[2] - pivot[2];
            box[0] = Math.min(box[0], x / 16f);
            box[1] = Math.min(box[1], y / 16f);
            box[2] = Math.min(box[2], z / 16f);
            box[3] = Math.max(box[3], (x + size[0]) / 16f);
            box[4] = Math.max(box[4], (y + size[1]) / 16f);
            box[5] = Math.max(box[5], (z + size[2]) / 16f);
        }
        return box;
    }

    /** Where a blow from {@code from} to {@code to} lands: the first limb in its way, or the nearest for a graze. */
    public static Strike strike(LivingEntity entity, Vec3 from, Vec3 to) {
        Vec3 feet = entity.position();
        Vec3 way = to.subtract(from);
        Limb hit = null;
        Matrix4f hitInverse = null;
        float nearest = Float.MAX_VALUE;
        Vector3f hitLocal = null;
        for (Limb limb : limbs(entity)) {
            Matrix4f inverse = new Matrix4f(limb.frame()).invert();
            Vector3f a = inverse.transformPosition((float) (from.x - feet.x), (float) (from.y - feet.y), (float) (from.z - feet.z), new Vector3f());
            Vector3f b = inverse.transformPosition((float) (to.x - feet.x), (float) (to.y - feet.y), (float) (to.z - feet.z), new Vector3f());
            float t = rayIntoBox(a, b, limb.box());
            if (t < 0f || t >= nearest) continue;
            nearest = t;
            hit = limb;
            hitInverse = inverse;
            hitLocal = new Vector3f(a).lerp(b, t);
        }
        if (hit != null) return placed(hit, hitInverse, from.add(way.scale(nearest)), hitLocal, way, true);
        Vec3 near = entity.getBoundingBox().inflate(GRAZE).clip(from, to).orElse(from);
        return nearestLimb(entity, near, way);
    }

    /** The limb nearest a point, for something that isn't on a limb: a tail's root, or a graze. */
    public static Strike nearestLimb(LivingEntity entity, Vec3 point, Vec3 way) {
        Vec3 feet = entity.position();
        Limb best = null;
        Matrix4f bestInverse = null;
        Vector3f bestLocal = null;
        float closest = Float.MAX_VALUE;
        for (Limb limb : limbs(entity)) {
            Matrix4f inverse = new Matrix4f(limb.frame()).invert();
            Vector3f local = inverse.transformPosition((float) (point.x - feet.x), (float) (point.y - feet.y), (float) (point.z - feet.z), new Vector3f());
            float[] box = limb.box();
            local.set(Mth.clamp(local.x, box[0], box[3]), Mth.clamp(local.y, box[1], box[4]), Mth.clamp(local.z, box[2], box[5]));
            Vector3f on = limb.frame().transformPosition(local, new Vector3f());
            float distance = on.distanceSquared((float) (point.x - feet.x), (float) (point.y - feet.y), (float) (point.z - feet.z));
            if (distance < closest) {
                closest = distance;
                best = limb;
                bestInverse = inverse;
                bestLocal = local;
            }
        }
        if (best == null) return unplaced(entity, point, way);
        return placed(best, bestInverse, point, bestLocal, way, false);
    }

    /** Just a point, on a creature whose limbs nobody tracks. */
    public static Strike bare(Vec3 point) {
        return new Strike(point, "", new Vector3f(), new Vector3f(), new Vector3f(0f, 0f, 1f), false);
    }

    /** A point on a body with no limb to it: {@code modelPoint} is only as good as the pose at rest. */
    public static Strike unplaced(LivingEntity entity, Vec3 point, Vec3 way) {
        return new Strike(point, "", new Vector3f(), modelPoint(entity, point), modelDirection(entity, way), false);
    }

    private static Strike placed(Limb limb, Matrix4f inverse, Vec3 world, Vector3f local, Vec3 way, boolean exact) {
        Vector3f at = limb.rest().transformPosition(local, new Vector3f());
        Vector3f direction = inverse.transformDirection((float) way.x, (float) way.y, (float) way.z, new Vector3f());
        limb.rest().transformDirection(direction);
        direction.set(direction.x, -direction.y, direction.z);
        if (direction.lengthSquared() < 1e-8f) direction.set(0f, 0f, 1f);
        return new Strike(world, limb.name(), new Vector3f(local), new Vector3f(at.x * 16f, -at.y * 16f, at.z * 16f), direction.normalize(), exact);
    }

    /** How far along the line from {@code a} to {@code b} it enters the box, or -1 if it doesn't. */
    private static float rayIntoBox(Vector3f a, Vector3f b, float[] box) {
        float enter = 0f, exit = 1f;
        float[] from = {a.x, a.y, a.z}, step = {b.x - a.x, b.y - a.y, b.z - a.z};
        for (int i = 0; i < 3; i++) {
            if (Math.abs(step[i]) < 1e-7f) {
                if (from[i] < box[i] || from[i] > box[i + 3]) return -1f;
                continue;
            }
            float near = (box[i] - from[i]) / step[i], far = (box[i + 3] - from[i]) / step[i];
            if (near > far) {
                float swap = near;
                near = far;
                far = swap;
            }
            enter = Math.max(enter, near);
            exit = Math.min(exit, far);
            if (enter > exit) return -1f;
        }
        return enter;
    }

    /** Where a point on a limb is in the world now, or null when this body has no such limb. */
    @Nullable
    public static Vec3 limbPoint(LivingEntity entity, String limb, Vector3f local) {
        Matrix4f root = root(entity);
        RigDefinition rig = rigOf(entity);
        Matrix4f frame = rig == null || !generic(rig) ? humanoidFrame(root, entity, limb)
                : rig.modelType() == RigDefinition.ModelType.GEOMETRY ? rigBoneFrame(root, entity, rig, limb, true) : null;
        if (frame == null) return null;
        Vector3f at = frame.transformPosition(local, new Vector3f());
        return entity.position().add(at.x, at.y, at.z);
    }

    /** A point in the world in this body's model, in model pixels with y up, as a Trace's surface has it. */
    public static org.joml.Vector3f modelPoint(LivingEntity entity, net.minecraft.world.phys.Vec3 world) {
        org.joml.Vector3f at = new Matrix4f(root(entity)).invert().transformPosition(
                (float) (world.x - entity.getX()), (float) (world.y - entity.getY()), (float) (world.z - entity.getZ()), new org.joml.Vector3f());
        return at.set(at.x * 16f, -at.y * 16f, at.z * 16f);
    }

    /** A direction in the world in this body's model, as {@link #modelPoint}. */
    public static org.joml.Vector3f modelDirection(LivingEntity entity, net.minecraft.world.phys.Vec3 world) {
        org.joml.Vector3f way = new Matrix4f(root(entity)).invert().transformDirection(
                (float) world.x, (float) world.y, (float) world.z, new org.joml.Vector3f());
        way.set(way.x, -way.y, way.z);
        return way.lengthSquared() < 1e-8f ? way.set(0f, 0f, 1f) : way.normalize();
    }

    /** The bones starting each piece of this attachment's model now. Fewer than two means it's all one piece. */
    public static List<String> pieceRoots(LivingEntity entity, AttachmentDef def) {
        AttachmentBody.Part part = def.body().part();
        if (part == null || !part.split()) return List.of();
        BodySkeleton skeleton = skeletonFor(def, stageOf(entity, def), entity.level().isClientSide);
        return skeleton == null || skeleton.pieceRoots.size() < 2 ? List.of() : skeleton.pieceRoots;
    }

    /** How many separate pieces a body wears: each branch of the model, at each place it fills. */
    public static int pieces(LivingEntity entity, AttachmentDef def) {
        return Math.max(1, anchors(entity, def, rigOf(entity)).size()) * Math.max(1, pieceRoots(entity, def).size());
    }

    /** Which piece a box of {@link #measure} is on. */
    public static int pieceOfBox(LivingEntity entity, AttachmentDef def, int box) {
        BodySkeleton skeleton = skeletonFor(def, stageOf(entity, def), entity.level().isClientSide);
        if (skeleton == null || skeleton.groups == 0) return 0;
        int splits = Math.max(1, pieceRoots(entity, def).size());
        int group = box % skeleton.groups;
        return box / skeleton.groups * splits + (splits > 1 ? skeleton.groupPiece[group] : 0);
    }

    /** Which of the places this attachment fills a box of {@link #measure} is at. */
    public static int anchorOfBox(LivingEntity entity, AttachmentDef def, int box) {
        BodySkeleton skeleton = skeletonFor(def, stageOf(entity, def), entity.level().isClientSide);
        return skeleton == null || skeleton.groups == 0 ? 0 : box / skeleton.groups;
    }

    /** From the model at one place this attachment fills to the bearer's feet in world axes, or null when it isn't on this body. */
    @Nullable
    public static Matrix4f anchorFrame(LivingEntity entity, AttachmentDef def, int anchor) {
        Setup setup = setup(entity, def);
        if (setup == null || anchor < 0 || anchor >= setup.anchors().size()) return null;
        return frame(setup, entity, setup.anchors().get(anchor));
    }

    /** Each box of this attachment now, from the feet in world axes: six floats a box. Empty when it isn't showing. */
    public static float[] measure(LivingEntity entity, AttachmentDef def) {
        Setup setup = setup(entity, def);
        if (setup == null) return new float[0];
        float[] out = new float[setup.anchors().size() * setup.skeleton().groups * 6];
        int filled = 0;
        Blocks blocks = blocksAround(entity);
        for (int a = 0; a < setup.anchors().size(); a++) {
            Anchor anchor = setup.anchors().get(a);
            Matrix4f frame = frame(setup, entity, anchor);
            if (frame == null) continue;
            float[] one = solve(setup.skeleton(), frame, setup.posed(), def.physics(), anchor.mirror(),
                    BodyPoseSim.running(entity, def, a), blocks);
            if (BodyParts.anyLost(entity, def.id())) {
                int splits = Math.max(1, pieceRoots(entity, def).size());
                for (int g = 0; g < setup.skeleton().groups; g++) {
                    int split = splits > 1 ? setup.skeleton().groupPiece[g] : 0;
                    if (!BodyParts.pieceLost(entity, def.id(), a * splits + split)) continue;
                    // Inside out, as a group with nothing showing is, so the other pieces keep their places.
                    one[g * 6] = one[g * 6 + 1] = one[g * 6 + 2] = Float.MAX_VALUE;
                    one[g * 6 + 3] = one[g * 6 + 4] = one[g * 6 + 5] = -Float.MAX_VALUE;
                }
            }
            System.arraycopy(one, 0, out, filled, one.length);
            filled += one.length;
        }
        return filled == out.length ? out : java.util.Arrays.copyOf(out, filled);
    }

    /** How many boxes this attachment has, which is how many part entities it wants. */
    public static int boxCount(LivingEntity entity, AttachmentDef def) {
        BodySkeleton skeleton = skeletonFor(def, stageOf(entity, def), entity.level().isClientSide);
        return skeleton == null ? 0 : skeleton.groups * anchors(entity, def, rigOf(entity)).size();
    }

    private static Blocks blocksAround(LivingEntity entity) {
        return (head, tail, n, into) -> ChainSim.gather(entity, head, tail, n, entity.getX(), entity.getY(), entity.getZ(), into);
    }

    /** Each server tick: lets the clip rules note any clip with a set length starting on this body. */
    static void tickClips(LivingEntity entity) {
        BodyPoseRules.tickTimedClips(entity, WornAttachments.of(entity), (sha, name) -> clip(sha, name, false));
    }

    /** One tick of this attachment's running chains on a body that's awake. */
    static void step(LivingEntity entity, AttachmentDef def, BodyPoseSim.Awake awake, float seconds) {
        Setup setup = setup(entity, def);
        if (setup == null) return;
        Blocks blocks = blocksAround(entity);
        Vector3f still = new Vector3f();
        float limb = entity.walkAnimation.position();
        float bob = GAIT_BOB * Math.min(1f, entity.walkAnimation.speed()) * Math.abs(Mth.sin(limb * 0.6662f));
        for (int a = 0; a < setup.anchors().size(); a++) {
            Anchor anchor = setup.anchors().get(a);
            Matrix4f frame = frame(setup, entity, anchor);
            ChainSim[] sims = awake.of(def, a, setup.anchors().size());
            if (frame == null || sims == null) continue;
            float size = frame.getScale(new Vector3f()).x;
            float sizeFactor = Mth.clamp(1f / (float) Math.sqrt(Math.max(0.05f, size)), 0.6f, 1.8f);
            List<ChainSim.Capsule> capsules = capsules(setup.root(), entity, setup.rig());
            Map<BodySkeleton.Node, Local> locals = locals(setup.skeleton(), setup.posed(), anchor.mirror());
            for (int c = 0; c < def.physics().size() && c < sims.length; c++) {
                AttachmentDef.PhysicsChain chain = def.physics().get(c);
                Captured captured = capture(chain, setup.skeleton(), locals, frame, anchor.mirror());
                if (captured == null) continue;
                ChainSim sim = sims[c];
                if (sim.n != captured.n || sim.tail == null) sim.allocate(captured.n);
                boolean fresh = !sim.ready;
                if (!fresh) sim.rollPose();
                sim.head.set(captured.head);
                for (int i = 0; i < captured.n; i++) {
                    sim.tail[i].set(captured.tail[i]);
                    sim.pitchAxis[i].set(captured.axis[i]);
                    sim.halfThickness[i] = thickness(captured.bones.get(i));
                }
                if (fresh) {
                    // A body waking up starts where it was at rest, not from its unbent pose.
                    sim.rollPose();
                    Quaternionf[] rest = settled(chain, captured, sim, size, blocks);
                    for (int i = 0; i < captured.n; i++) {
                        sim.dev[i].set(rest[i]);
                        sim.prevDev[i].set(rest[i]);
                        sim.pos[i].set(captured.points[i]);
                        sim.target[i].set(sim.pos[i]);
                        sim.vel[i].zero();
                    }
                    sim.ready = true;
                } else {
                    sim.shift(-awake.pathX * seconds, -awake.pathY * seconds, -awake.pathZ * seconds);
                    sim.wet(entity.level(), entity.getX(), entity.getY(), entity.getZ(), seconds);
                    if (chain.blocks()) sim.gatherBlocks(entity, entity.getX(), entity.getY(), entity.getZ());
                    else sim.boxCount = 0;
                    sim.simulate(chain, seconds, bob, chain.scaleWithSize() ? sizeFactor : 1f, a * 1.7f + c * 1.1f, capsules, size, still,
                            (float) (sim.boxX - entity.getX()), (float) (sim.boxY - entity.getY()), (float) (sim.boxZ - entity.getZ()));
                }
                sim.lastBob = bob;
                write(chain, captured, sim.dev, locals, frame);
            }
        }
    }

    private static float thickness(BodySkeleton.Node node) {
        float measured = node.halfThickness();
        return measured > 0f ? Mth.clamp(measured, RADIUS_MIN, RADIUS_MAX) : RADIUS_FALLBACK;
    }

    /** The capsules this body's chains rest against, in its frame. */
    private static List<ChainSim.Capsule> capsules(Matrix4f root, LivingEntity entity, @Nullable RigDefinition rig) {
        List<RigDefinition.Collider> defs;
        if (rig == null) {
            defs = RigDefinition.HUMANOID_COLLIDERS;
        } else if (rig.colliders() != null) {
            defs = rig.colliders();
        } else {
            defs = generic(rig) ? List.of() : RigDefinition.HUMANOID_COLLIDERS;
        }
        List<ChainSim.Capsule> out = new ArrayList<>(defs.size());
        for (RigDefinition.Collider def : defs) {
            Matrix4f frame = boneFrame(root, entity, rig, def.bone());
            if (frame == null) continue;
            Vector3f a = frame.transformPosition(def.from()[0] / 16f, def.from()[1] / 16f, def.from()[2] / 16f, new Vector3f());
            Vector3f b = frame.transformPosition(def.to()[0] / 16f, def.to()[1] / 16f, def.to()[2] / 16f, new Vector3f());
            out.add(new ChainSim.Capsule(def.name(), a, b, def.radius() / 16f * frame.getScale(new Vector3f()).x));
        }
        return out;
    }

    private static Posed posed(LivingEntity entity, AttachmentDef def) {
        Map<String, float[]> rotations = new LinkedHashMap<>(BodyPoseRules.poseBones(entity, def));
        Map<String, float[]> positions = new LinkedHashMap<>();
        Map<String, float[]> scales = new LinkedHashMap<>();
        boolean isClient = entity.level().isClientSide;
        BodyPoseRules.sampleClips(entity, def, (sha, name) -> clip(sha, name, isClient), rotations, positions, scales);
        return new Posed(rotations, positions, scales, List.of());
    }

    private static Posed withMorphs(Posed posed, BodyPoseRules.Morphs morphs) {
        if (morphs == BodyPoseRules.Morphs.NONE) return posed;
        Map<String, float[]> rotations = new LinkedHashMap<>(morphs.rotations());
        posed.rotations().forEach((bone, delta) -> rotations.merge(bone, delta,
                (a, b) -> new float[]{a[0] + b[0], a[1] + b[1], a[2] + b[2]}));
        Map<String, float[]> scales = new LinkedHashMap<>(morphs.boneScales());
        posed.scales().forEach((bone, scale) -> scales.merge(bone, scale,
                (a, b) -> new float[]{a[0] * b[0], a[1] * b[1], a[2] * b[2]}));
        return new Posed(rotations, posed.positions(), scales, morphs.hiddenBones());
    }

    @Nullable
    private static AttachmentDef.StageOverride stageOf(LivingEntity entity, AttachmentDef def) {
        if (def.stages().isEmpty()) return null;
        String named = "", canonical = "adult";
        if (entity.level().isClientSide && client != null) {
            String[] keys = client.stage(entity);
            named = keys[0];
            canonical = keys[1];
        } else if (entity instanceof VillagerEntityMCA villager) {
            LifeStage stage = LifeStageProgression.currentStage(villager);
            if (stage != null) named = stage.id();
            canonical = stage != null && stage.presentsAs() == CanonicalStage.SENIOR ? "senior"
                    : villager.getAgeState().name().toLowerCase(java.util.Locale.ROOT);
        }
        AttachmentDef.StageOverride override = def.stages().get(named);
        return override != null ? override : def.stages().get(canonical);
    }

    @Nullable
    private static BodySkeleton skeletonFor(AttachmentDef def, @Nullable AttachmentDef.StageOverride stage, boolean isClient) {
        String sha = stage != null && stage.geoSha1() != null ? stage.geoSha1() : def.geoSha1();
        StringBuilder spec = new StringBuilder(sha);
        for (AttachmentDef.PhysicsChain chain : def.physics()) {
            if (chain.segments() > 1 && chain.bones().size() == 1) {
                spec.append('|').append(chain.bones().get(0)).append(':').append(chain.segments()).append(':').append(chain.axis());
            }
        }
        String key = spec.toString();
        BodySkeleton cached = SKELETONS.get(key);
        if (cached != null) return cached;
        JsonObject geometry = isClient ? (client == null ? null : client.geometry(sha)) : serverGeometry(sha);
        // Still downloading: try again next time rather than remembering nothing.
        if (geometry == null) return null;
        Map<String, Integer> groups = HitShapes.groups(geometry);
        JsonObject sliced = geometry.deepCopy();
        for (AttachmentDef.PhysicsChain chain : def.physics()) {
            if (chain.segments() > 1 && chain.bones().size() == 1) {
                sliced = AttachmentGeoSegmenter.segment(sliced, chain.bones().get(0), chain.segments(), chain.axis());
            }
        }
        BodySkeleton built = BodySkeleton.parse(sliced, groups);
        if (built != null) {
            built.pieceRoots = List.copyOf(HitShapes.pieceRoots(geometry));
            built.groupPiece = HitShapes.groupPieces(geometry, groups, built.pieceRoots);
            SKELETONS.put(key, built);
        }
        return built;
    }

    @Nullable
    private static JsonObject serverGeometry(String sha) {
        AttachmentServerData.Blob blob = AttachmentServerData.blob(sha);
        return blob == null ? null : parse(blob.bytes());
    }

    @Nullable
    private static JsonObject parse(byte[] bytes) {
        try {
            return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Nullable
    private static AttachmentAnimation.Clip clip(String sha, String name, boolean isClient) {
        if (isClient) return client == null ? null : client.clip(sha, name);
        Map<String, AttachmentAnimation.Clip> clips = CLIPS.computeIfAbsent(sha, BodyPose::parseBlobClips);
        return named(clips, name);
    }

    private static Map<String, AttachmentAnimation.Clip> parseBlobClips(String sha) {
        AttachmentServerData.Blob blob = AttachmentServerData.blob(sha);
        return blob == null ? Map.of() : parseClips(blob.bytes());
    }

    private static Map<String, AttachmentAnimation.Clip> parseClips(byte[] bytes) {
        JsonObject json = parse(bytes);
        return json == null ? Map.of() : AttachmentAnimation.parse(json, null);
    }

    @Nullable
    private static AttachmentAnimation.Clip named(@Nullable Map<String, AttachmentAnimation.Clip> clips, String name) {
        if (clips == null || clips.isEmpty()) return null;
        if (name.isEmpty()) return clips.values().iterator().next();
        AttachmentAnimation.Clip exact = clips.get(name);
        if (exact != null) return exact;
        for (var entry : clips.entrySet()) {
            if (entry.getKey().endsWith("." + name)) return entry.getValue();
        }
        return null;
    }

    private static AllelePayload rolled(LivingEntity entity, AttachmentDef def) {
        if (entity.level().isClientSide) return client == null ? AllelePayload.parse("") : client.rolled(entity, def);
        for (var power : com.aetherianartificer.townstead.pheno.power.Powers.active(entity)) {
            if (!(power.component() instanceof com.aetherianartificer.townstead.root.gene.types.AttachmentGeneType.Instance instance)) continue;
            if (!instance.attachments().contains(def.id())) continue;
            for (Allele allele : com.aetherianartificer.townstead.root.gene.AspectOverlay.apply(entity,
                    com.aetherianartificer.townstead.root.Heredity.expressedAlleles(
                            com.aetherianartificer.townstead.root.ExpressedGenes.genotypeOf(entity)))) {
                if (power.id().equals(allele.geneId())) {
                    return AllelePayload.parse(allele.variantId() == null ? "" : allele.variantId());
                }
            }
        }
        return AllelePayload.parse("");
    }

    @Nullable
    private static RigDefinition rigOf(LivingEntity entity) {
        if (entity.level().isClientSide) {
            if (client == null) return null;
            String id = client.rigId(entity);
            return id == null ? null : client.rig(id);
        }
        return ServerRig.defFor(entity);
    }

    private static List<Anchor> anchors(LivingEntity entity, AttachmentDef def, @Nullable RigDefinition rig) {
        boolean isClient = entity.level().isClientSide;
        List<AttachmentPointDef> points = isClient ? (client == null ? List.of() : client.points()) : AttachmentServerData.slots();
        String rigId = isClient ? (client == null ? "" : client.rigId(entity)) : ServerRig.rigIdFor(entity);
        if (rigId == null) rigId = "";
        if (def.targetTag() != null) {
            List<AttachmentPointDef> matched = new ArrayList<>();
            boolean rigSpecific = false;
            for (AttachmentPointDef point : points) {
                if (!point.tags().contains(def.targetTag())) continue;
                if (!point.rig().isEmpty() && !point.rig().equals(rigId)) continue;
                if (!point.rig().isEmpty()) rigSpecific = true;
                matched.add(point);
            }
            if (rigSpecific) matched.removeIf(point -> point.rig().isEmpty());
            List<Anchor> out = new ArrayList<>(matched.size());
            for (AttachmentPointDef point : matched) out.add(new Anchor(point.bone(), point.offset(), point.rotation(), point.mirror()));
            return out;
        }
        if (def.targetPoint() != null) {
            for (AttachmentPointDef point : points) {
                if (!point.id().equals(def.targetPoint())) continue;
                if (point.rig().isEmpty() || point.rig().equals(rigId)) {
                    return List.of(new Anchor(point.bone(), point.offset(), point.rotation(), point.mirror()));
                }
                return List.of();
            }
        }
        return List.of(new Anchor(def.bone(), new float[3], new float[3], false));
    }

    /** From the model to the bearer's frame: origin at the feet, world axes. As the entity renderer goes. */
    private static Matrix4f root(LivingEntity entity) {
        Matrix4f m = new Matrix4f();
        RigHitboxes.Inputs inputs = RigHitboxes.inputs(entity);
        if (inputs != null) {
            float shared = inputs.growth() * inputs.rigScale() * inputs.entityScale();
            m.scale(inputs.widthScale() * shared, inputs.heightScale() * shared, inputs.widthScale() * shared);
        }
        boolean lying = entity.hasPose(Pose.SLEEPING);
        Direction bed = lying ? entity.getBedOrientation() : null;
        if (bed != null) {
            float toHead = entity.getEyeHeight(Pose.STANDING) - 0.1f;
            m.translate(-bed.getStepX() * toHead, 0f, -bed.getStepZ() * toHead);
        }
        if (lying) {
            float turn = bed == null ? entity.yBodyRot : switch (bed) {
                case SOUTH -> 90f;
                case NORTH -> 270f;
                case EAST -> 180f;
                default -> 0f;
            };
            m.rotateY(Mth.DEG_TO_RAD * turn).rotateZ(Mth.DEG_TO_RAD * 90f).rotateY(Mth.DEG_TO_RAD * 270f);
        } else {
            m.rotateY(Mth.DEG_TO_RAD * (180f - entity.yBodyRot));
            if (entity.isFallFlying()) {
                float ticks = entity.getFallFlyingTicks();
                m.rotateX(Mth.DEG_TO_RAD * Mth.clamp(ticks * ticks / 100f, 0f, 1f) * (-90f - entity.getXRot()));
            } else if (entity instanceof Player) {
                float swim = entity.getSwimAmount(1f);
                if (swim > 0f) {
                    m.rotateX(Mth.DEG_TO_RAD * Mth.lerp(swim, 0f, entity.isInWater() ? -90f - entity.getXRot() : -90f));
                    if (entity.isVisuallySwimming()) m.translate(0f, -1f, 0.3f);
                }
            }
        }
        m.scale(-1f, -1f, 1f);
        if (entity instanceof Player) m.scale(PLAYER_MODEL_SCALE);
        // MCA seats a baby on the ground this way unless something is carrying it.
        if (entity instanceof VillagerEntityMCA villager && villager.getAgeState() == AgeState.BABY && !villager.isPassenger()) {
            m.translate(0f, BABY_SEAT, 0f);
        }
        return m.translate(0f, -MODEL_FEET, 0f);
    }

    private static boolean generic(RigDefinition rig) {
        return rig.modelType() == RigDefinition.ModelType.GEOMETRY || VANILLA_PIVOTS.containsKey(rig.modelRef())
                || (rig.modelClass() != null && !rig.modelClass().isEmpty());
    }

    /**
     * Where an attachment hangs. Attachments ride the host body's bones, and a non-humanoid rig moves
     * those only through its back and head anchors and custom armor. Any other bone stays where the
     * host's own model puts it, and packs are authored against that.
     */
    @Nullable
    private static Matrix4f wornFrame(Matrix4f root, LivingEntity entity, @Nullable RigDefinition rig, String boneName) {
        if (rig == null || !generic(rig)) return boneFrame(root, entity, rig, boneName);
        String channel = "torso".equals(boneName) ? "body" : boneName;
        if (!RigDefinition.CHANNELS.contains(channel)) return null;
        RigDefinition.WornAnchor worn = "body".equals(channel) ? rig.back() : "head".equals(channel) ? rig.head() : null;
        boolean limb = channel.endsWith("_arm") || channel.endsWith("_leg");
        if (worn == null && !(limb && rig.armorType() == RigDefinition.ArmorType.CUSTOM)) return humanoidFrame(root, entity, channel);
        Matrix4f frame = boneFrame(root, entity, rig, channel);
        if (frame == null) return humanoidFrame(root, entity, channel);
        if (worn != null) {
            float[] offset = worn.base().offset();
            frame.rotate(degrees(worn.base().rotation()));
            frame.translate(offset[0] / 16f, offset[1] / 16f, offset[2] / 16f);
        }
        return frame;
    }

    /** The frame under a channel's bone. Null when this body has no such bone, which is when nothing is drawn. */
    @Nullable
    private static Matrix4f boneFrame(Matrix4f root, LivingEntity entity, @Nullable RigDefinition rig, String boneName) {
        String channel = "torso".equals(boneName) ? "body" : boneName;
        if (rig != null && !RigDefinition.CHANNELS.contains(channel)) return null;
        if (rig != null && rig.modelType() == RigDefinition.ModelType.GEOMETRY) {
            Matrix4f frame = rigGeometryFrame(root, entity, rig, channel);
            // The model can't be read here, so it's placed as a body of the usual shape.
            return frame != null || rigGeometry(rig.modelRef(), entity.level().isClientSide) != null ? frame : humanoidFrame(root, entity, channel);
        }
        if (rig != null) {
            Map<String, float[]> pivots = VANILLA_PIVOTS.get(rig.modelRef());
            if (pivots != null) {
                float[] pivot = pivots.get(rig.boneFor(channel));
                return pivot == null ? null : new Matrix4f(root).translate(pivot[0] / 16f, pivot[1] / 16f, pivot[2] / 16f);
            }
        }
        return humanoidFrame(root, entity, channel);
    }

    /** A humanoid body's bone, moved as its model moves it: head turn, crouch, and walking limbs. */
    @Nullable
    private static Matrix4f humanoidFrame(Matrix4f root, LivingEntity entity, String channel) {
        boolean crouching = entity.isCrouching();
        float limb = entity.walkAnimation.position() * 0.6662f;
        float swing = Math.min(entity.walkAnimation.speed(), 1f);
        float x, y, z = 0f, xRot = 0f, yRot = 0f;
        switch (channel) {
            case "head", "headwear" -> {
                x = 0f;
                y = crouching ? 4.2f : 0f;
                yRot = Mth.DEG_TO_RAD * Mth.wrapDegrees(entity.getYHeadRot() - entity.yBodyRot);
                xRot = Mth.DEG_TO_RAD * entity.getXRot();
            }
            case "body" -> {
                x = 0f;
                y = crouching ? 3.2f : 0f;
                xRot = crouching ? 0.5f : 0f;
            }
            case "right_arm" -> {
                x = -5f;
                y = crouching ? 5.2f : 2f;
                xRot = Mth.cos(limb + Mth.PI) * swing + (crouching ? 0.4f : 0f);
            }
            case "left_arm" -> {
                x = 5f;
                y = crouching ? 5.2f : 2f;
                xRot = Mth.cos(limb) * swing + (crouching ? 0.4f : 0f);
            }
            case "right_leg" -> {
                x = -1.9f;
                y = crouching ? 12.2f : 12f;
                z = crouching ? 4f : 0.1f;
                xRot = Mth.cos(limb) * 1.4f * swing;
            }
            case "left_leg" -> {
                x = 1.9f;
                y = crouching ? 12.2f : 12f;
                z = crouching ? 4f : 0.1f;
                xRot = Mth.cos(limb + Mth.PI) * 1.4f * swing;
            }
            default -> {
                return null;
            }
        }
        return new Matrix4f(root).translate(x / 16f, y / 16f, z / 16f).rotate(new Quaternionf().rotationZYX(0f, yRot, xRot));
    }

    @Nullable
    private static Matrix4f rigGeometryFrame(Matrix4f root, LivingEntity entity, RigDefinition rig, String channel) {
        return rigBoneFrame(root, entity, rig, rig.boneFor(channel), true);
    }

    /** The frame under a bone of a geometry rig, as it's posed now or, without {@code posed}, at rest. */
    @Nullable
    private static Matrix4f rigBoneFrame(Matrix4f root, LivingEntity entity, RigDefinition rig, String boneName, boolean posed) {
        boolean isClient = entity.level().isClientSide;
        JsonObject geometry = rigGeometry(rig.modelRef(), isClient);
        if (geometry == null) return null;
        var bones = HitShapes.bonesOf(geometry);
        if (bones == null) return null;
        Map<String, JsonObject> byName = new LinkedHashMap<>();
        for (var bone : bones) {
            JsonObject json = bone.getAsJsonObject();
            byName.put(json.get("name").getAsString(), json);
        }
        JsonObject bone = byName.get(boneName);
        if (bone == null) return null;
        List<JsonObject> chain = new ArrayList<>();
        for (JsonObject at = bone; at != null && chain.size() <= byName.size();
             at = at.has("parent") ? byName.get(at.get("parent").getAsString()) : null) {
            chain.add(0, at);
        }
        Map<String, float[]> deltas = posed ? BodyPoseRules.rigBones(entity, rig, ref -> rigAnimations(ref, isClient)) : Map.of();
        // A rig stands on the ground: its roots are placed from y=24 of model space, where the feet are.
        Matrix4f m = new Matrix4f(root);
        float[] parent = {0f, 24f, 0f};
        for (JsonObject link : chain) {
            float[] pivot = BodySkeleton.vec(link, "pivot");
            float[] rotation = BodySkeleton.vec(link, "rotation");
            float[] delta = deltas.get(link.get("name").getAsString());
            float dx = delta == null ? 0f : delta[3], dy = delta == null ? 0f : delta[4], dz = delta == null ? 0f : delta[5];
            m.translate((pivot[0] - parent[0] + dx) / 16f, (-(pivot[1] - parent[1]) + dy) / 16f, (pivot[2] - parent[2] + dz) / 16f);
            m.rotate(new Quaternionf().rotationZYX(Mth.DEG_TO_RAD * rotation[2] + (delta == null ? 0f : delta[2]),
                    Mth.DEG_TO_RAD * rotation[1] + (delta == null ? 0f : delta[1]),
                    Mth.DEG_TO_RAD * rotation[0] + (delta == null ? 0f : delta[0])));
            parent = pivot;
        }
        return m;
    }

    /** A rig's model by id: a pack file, or another mod's own asset, read from its jar on a server. */
    @Nullable
    private static JsonObject rigGeometry(String ref, boolean isClient) {
        if (isClient) return client == null ? null : client.rigGeometry(ref);
        return RIG_GEOMETRY.computeIfAbsent(ref, key -> {
            String sha = AttachmentServerData.namedGeo().get(key);
            if (sha != null) return Optional.ofNullable(serverGeometry(sha));
            byte[] bytes = ModAssets.read(key);
            if (bytes == null) return Optional.empty();
            if (key.endsWith(".bbmodel")) {
                bytes = BbmodelConverter.geometry(bytes, key.substring(key.lastIndexOf('/') + 1, key.length() - ".bbmodel".length()), "");
                if (bytes == null) return Optional.empty();
            }
            return Optional.ofNullable(parse(bytes));
        }).orElse(null);
    }

    @Nullable
    private static Map<String, AttachmentAnimation.Clip> rigAnimations(String ref, boolean isClient) {
        if (isClient) return client == null ? null : client.rigAnimations(ref);
        return RIG_ANIMATIONS.computeIfAbsent(ref, key -> {
            String sha = AttachmentServerData.namedAnimations().get(key);
            if (sha != null) return parseBlobClips(sha);
            byte[] bytes = ModAssets.read(key);
            return bytes == null ? Map.of() : parseClips(bytes);
        });
    }

    private static Quaternionf degrees(float[] zyx) {
        return new Quaternionf().rotationZYX(Mth.DEG_TO_RAD * zyx[2], Mth.DEG_TO_RAD * zyx[1], Mth.DEG_TO_RAD * zyx[0]);
    }

    // --- The numbers --------------------------------------------------------------------------

    /** One bone's values for this moment, on the bake the render layer would draw. */
    private static final class Local {
        float x, y, z, xRot, yRot, zRot, xScale = 1f, yScale = 1f, zScale = 1f;
        Quaternionf settled;

        Quaternionf rest(float pitch, Quaternionf dest) {
            return dest.rotationZYX(zRot, yRot, xRot + pitch);
        }

        Matrix4f apply(Matrix4f m, Quaternionf rotation) {
            return m.translate(x / 16f, y / 16f, z / 16f).rotate(rotation).scale(xScale, yScale, zScale);
        }

        Matrix4f applyPosed(Matrix4f m) {
            return apply(m, settled != null ? settled : rest(0f, new Quaternionf()));
        }
    }

    private static Map<BodySkeleton.Node, Local> locals(BodySkeleton skeleton, Posed posed, boolean mirror) {
        Map<BodySkeleton.Node, Local> locals = new IdentityHashMap<>();
        for (BodySkeleton.Node node : skeleton.byName.values()) locals.put(node, local(node, posed, mirror));
        return locals;
    }

    /** Six floats per group of bones: the box around where they all are under {@code frame}. */
    static float[] solve(BodySkeleton skeleton, Matrix4f frame, Posed posed, List<AttachmentDef.PhysicsChain> chains, boolean mirror) {
        return solve(skeleton, frame, posed, chains, mirror, null, null);
    }

    static float[] solve(BodySkeleton skeleton, Matrix4f frame, Posed posed, List<AttachmentDef.PhysicsChain> chains, boolean mirror,
                         @Nullable ChainSim[] running, @Nullable Blocks blocks) {
        Map<BodySkeleton.Node, Local> locals = locals(skeleton, posed, mirror);
        ChainSim scratch = chains.isEmpty() ? null : new ChainSim();
        float size = frame.getScale(new Vector3f()).x;
        for (int c = 0; c < chains.size(); c++) {
            AttachmentDef.PhysicsChain chain = chains.get(c);
            Captured captured = capture(chain, skeleton, locals, frame, mirror);
            if (captured == null) continue;
            ChainSim sim = running != null && c < running.length ? running[c] : null;
            boolean live = sim != null && sim.ready && sim.n == captured.n;
            Quaternionf[] dev = live ? sim.dev : settled(chain, captured, scratch, size, chain.blocks() ? blocks : null);
            write(chain, captured, dev, locals, frame);
        }
        float[] out = new float[skeleton.groups * 6];
        for (int i = 0; i < out.length; i += 6) {
            out[i] = out[i + 1] = out[i + 2] = Float.MAX_VALUE;
            out[i + 3] = out[i + 4] = out[i + 5] = -Float.MAX_VALUE;
        }
        Set<String> hidden = Set.copyOf(posed.hidden());
        for (BodySkeleton.Node root : skeleton.roots) walk(root, frame, locals, hidden, mirror, out);
        return out;
    }

    private static Local local(BodySkeleton.Node node, Posed posed, boolean mirror) {
        Local local = new Local();
        local.x = mirror ? -node.x : node.x;
        local.y = node.y;
        local.z = node.z;
        local.xRot = node.xRot;
        local.yRot = mirror ? -node.yRot : node.yRot;
        local.zRot = mirror ? -node.zRot : node.zRot;
        float[] rotation = posed.rotations().get(node.name);
        if (rotation != null) {
            local.xRot += Mth.DEG_TO_RAD * rotation[0];
            local.yRot += Mth.DEG_TO_RAD * (mirror ? rotation[1] : -rotation[1]);
            local.zRot += Mth.DEG_TO_RAD * (mirror ? rotation[2] : -rotation[2]);
        }
        float[] position = posed.positions().get(node.name);
        if (position != null) {
            local.x += mirror ? -position[0] : position[0];
            local.y += -position[1];
            local.z += position[2];
        }
        float[] scale = posed.scales().get(node.name);
        if (scale != null) {
            local.xScale = scale[0];
            local.yScale = scale[1];
            local.zScale = scale[2];
        }
        return local;
    }

    private static void walk(BodySkeleton.Node node, Matrix4f parent, Map<BodySkeleton.Node, Local> locals, Set<String> hidden,
                             boolean mirror, float[] out) {
        if (hidden.contains(node.name)) return;
        Matrix4f world = locals.get(node).applyPosed(new Matrix4f(parent));
        float[] bounds = node.bounds(mirror);
        if (bounds != null) {
            int at = node.group * 6;
            Vector3f corner = new Vector3f();
            for (int c = 0; c < 8; c++) {
                corner.set((c & 1) == 0 ? bounds[0] : bounds[3], (c & 2) == 0 ? bounds[1] : bounds[4], (c & 4) == 0 ? bounds[2] : bounds[5]);
                world.transformPosition(corner);
                out[at] = Math.min(out[at], corner.x);
                out[at + 1] = Math.min(out[at + 1], corner.y);
                out[at + 2] = Math.min(out[at + 2], corner.z);
                out[at + 3] = Math.max(out[at + 3], corner.x);
                out[at + 4] = Math.max(out[at + 4], corner.y);
                out[at + 5] = Math.max(out[at + 5], corner.z);
            }
        }
        for (BodySkeleton.Node child : node.children) walk(child, world, locals, hidden, mirror, out);
    }

    /** A chain's animated pose: where each segment is headed before physics has its say. */
    private static final class Captured {
        List<BodySkeleton.Node> bones;
        List<List<BodySkeleton.Node>> between;
        int n;
        float restPitch;
        final Vector3f head = new Vector3f();
        Vector3f[] tail, axis, tailLocal, points;
    }

    @Nullable
    private static Captured capture(AttachmentDef.PhysicsChain chain, BodySkeleton skeleton, Map<BodySkeleton.Node, Local> locals,
                                    Matrix4f frame, boolean mirror) {
        Captured out = new Captured();
        out.bones = new ArrayList<>();
        out.between = new ArrayList<>();
        out.between.add(List.of());
        for (String name : chain.effectiveBones()) {
            BodySkeleton.Node node = skeleton.byName.get(name);
            if (node == null) break;
            if (!out.bones.isEmpty()) {
                List<BodySkeleton.Node> path = pathBetween(out.bones.get(out.bones.size() - 1), node);
                if (path == null) break;
                out.between.add(path);
            }
            out.bones.add(node);
        }
        int n = out.n = out.bones.size();
        if (n == 0) return null;
        out.restPitch = Mth.DEG_TO_RAD * chain.restPitch();
        out.tail = ChainSim.vectors(n);
        out.axis = ChainSim.vectors(n);
        out.tailLocal = ChainSim.vectors(n);
        out.points = ChainSim.vectors(n);
        Matrix4f world = parentWorld(out.bones.get(0), locals, frame);
        Quaternionf q = new Quaternionf();
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                for (BodySkeleton.Node mid : out.between.get(i)) locals.get(mid).apply(world, locals.get(mid).rest(0f, q));
            }
            Local local = locals.get(out.bones.get(i));
            local.apply(world, local.rest(out.restPitch, q));
            world.getTranslation(i == 0 ? out.head : out.tail[i - 1]);
            world.transformDirection(new Vector3f(1f, 0f, 0f), out.axis[i]);
            if (out.axis[i].lengthSquared() > 1e-8f) out.axis[i].normalize();
        }
        for (int i = 0; i < n - 1; i++) {
            Matrix4f inner = new Matrix4f();
            for (BodySkeleton.Node mid : out.between.get(i + 1)) locals.get(mid).apply(inner, locals.get(mid).rest(0f, q));
            Local next = locals.get(out.bones.get(i + 1));
            inner.transformPosition(new Vector3f(next.x / 16f, next.y / 16f, next.z / 16f), out.tailLocal[i]);
        }
        float[] tip = out.bones.get(n - 1).tip(mirror);
        if (tip != null) out.tailLocal[n - 1].set(tip[0], tip[1], tip[2]);
        else if (n > 1) out.tailLocal[n - 1].set(out.tailLocal[n - 2]).mul(0.5f);
        else return null;
        world.transformPosition(out.tailLocal[n - 1], out.tail[n - 1]);
        return out;
    }

    /**
     * Where a physics chain comes to rest. Its spring pulls with {@code omega² (goal - p)} and gravity with
     * {@code omega² length g}, so a still chain sits at {@code goal + down * g * length}, whatever its
     * stiffness. That needs no simulating: one pass from root to tip, under the same limits as the sim,
     * laying each segment against whatever blocks it.
     */
    private static Quaternionf[] settled(AttachmentDef.PhysicsChain chain, Captured captured, ChainSim helper, float size,
                                         @Nullable Blocks blocks) {
        int n = captured.n;
        Quaternionf[] dev = new Quaternionf[n];
        int boxCount = blocks == null ? 0 : blocks.gather(captured.head, captured.tail, n, helper.boxes);
        Quaternionf carry = new Quaternionf();
        Vector3f parentHead = new Vector3f(captured.head);
        Vector3f restDir = new Vector3f(), aim = new Vector3f(), p = new Vector3f();
        for (int i = 0; i < n; i++) {
            dev[i] = new Quaternionf();
            float t = n == 1 ? 0f : i / (float) (n - 1);
            captured.tail[i].sub(i == 0 ? captured.head : captured.tail[i - 1], restDir);
            float length = restDir.length();
            if (length < 1e-5f) continue;
            restDir.div(length);
            carry.transform(restDir, aim);
            aim.mul(CARRY).add(new Vector3f(restDir).mul(1f - CARRY)).normalize();
            float gravity = Math.max(0f, chain.gravity().at(t));
            p.set(aim).mul(length).add(parentHead).fma(gravity * length, DOWN);
            float radius = (chain.radius() >= 0f ? chain.radius() : thickness(captured.bones.get(i))) / 16f * size;
            for (int pass = 0; pass < SETTLE_PASSES; pass++) {
                helper.constrain(chain, t, parentHead, p, aim, captured.axis[i], length);
                if (boxCount == 0 || !ChainCollision.outOfBoxes(parentHead, p, length, radius, helper.boxes, boxCount, 0f, 0f, 0f)) break;
            }
            captured.points[i].set(p);
            Vector3f direction = new Vector3f(p).sub(parentHead).normalize();
            dev[i].rotationTo(restDir, direction);
            carry.set(dev[i]);
            parentHead.set(p);
        }
        return dev;
    }

    /** Turns each bone so its segment points where {@code dev} says, root to tip, as the sim's write does. */
    private static void write(AttachmentDef.PhysicsChain chain, Captured captured, Quaternionf[] dev,
                              Map<BodySkeleton.Node, Local> locals, Matrix4f frame) {
        Matrix4f parent = parentWorld(captured.bones.get(0), locals, frame);
        Matrix4f inverse = new Matrix4f();
        Quaternionf q = new Quaternionf();
        Vector3f restDir = new Vector3f(), wanted = new Vector3f(), current = new Vector3f();
        for (int i = 0; i < captured.n; i++) {
            if (i > 0) {
                for (BodySkeleton.Node mid : captured.between.get(i)) locals.get(mid).apply(parent, locals.get(mid).rest(0f, q));
            }
            captured.tail[i].sub(i == 0 ? captured.head : captured.tail[i - 1], restDir);
            if (restDir.lengthSquared() < 1e-10f) return;
            restDir.normalize();
            dev[i].transform(restDir, wanted);
            inverse.set(parent).invert().transformDirection(wanted);
            Local local = locals.get(captured.bones.get(i));
            Quaternionf rest = local.rest(captured.restPitch, new Quaternionf());
            current.set(captured.tailLocal[i]).mul(local.xScale, local.yScale, local.zScale);
            rest.transform(current);
            if (current.lengthSquared() < 1e-10f || wanted.lengthSquared() < 1e-10f) return;
            current.normalize();
            wanted.normalize();
            local.settled = new Quaternionf().rotationTo(current, wanted).mul(rest);
            local.apply(parent, local.settled);
        }
    }

    private static List<BodySkeleton.Node> pathBetween(BodySkeleton.Node ancestor, BodySkeleton.Node bone) {
        List<BodySkeleton.Node> path = new ArrayList<>();
        for (BodySkeleton.Node at = bone.parent; at != null; at = at.parent) {
            if (at == ancestor) {
                java.util.Collections.reverse(path);
                return path;
            }
            path.add(at);
        }
        return null;
    }

    private static Matrix4f parentWorld(BodySkeleton.Node bone, Map<BodySkeleton.Node, Local> locals, Matrix4f frame) {
        List<BodySkeleton.Node> ancestors = new ArrayList<>();
        for (BodySkeleton.Node at = bone.parent; at != null; at = at.parent) ancestors.add(at);
        Matrix4f out = new Matrix4f(frame);
        for (int i = ancestors.size() - 1; i >= 0; i--) locals.get(ancestors.get(i)).applyPosed(out);
        return out;
    }
}
