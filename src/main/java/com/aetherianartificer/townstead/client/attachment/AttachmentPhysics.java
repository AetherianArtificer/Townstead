package com.aetherianartificer.townstead.client.attachment;

import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.client.accessibility.Accessibility;
import com.aetherianartificer.townstead.client.attachment.geo.AttachmentGeo;
import com.aetherianartificer.townstead.client.devlink.BenchGizmos;
import com.aetherianartificer.townstead.root.attachment.AttachmentDef;
import com.aetherianartificer.townstead.root.attachment.ChainSim;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client-side secondary motion for attachment bone chains. Each chain bone's tip is a
 * particle in the bearer's world frame that springs toward where the animated pose
 * (morphs, state poses, keyframe clips) puts it. Because the sim reads the anchor's
 * rendered transform, anything that moves the body drives it: walking, turning, head
 * motion, emotes, sleeping. Gravity points world-down whatever the body is doing.
 *
 * <p>Each child aims along its animated direction plus a share of its parent's swing,
 * so a swing travels down the chain as a wave without whipping the tip. The sim runs fixed substeps on real
 * time with the anchor interpolated across them, and renders interpolated between the
 * last two substeps. A gap (off-screen, teleport, new geometry) resets to the pose.
 * State is per entity, render context (world or GUI preview), definition, and anchor.</p>
 */
public final class AttachmentPhysics {

    private static final Map<Integer, EntityState> STATES = new HashMap<>();
    private static final long EVICT_AFTER_MS = 10_000;
    private static final float GAP_SECONDS = 0.3f;
    private static final float TELEPORT = 8f;
    // Seconds over which the bearer's speed changes reach the chains.
    private static final float PATH_SMOOTHING = 0.15f;
    // Simulation range from the camera, in blocks.
    private static final float FULL_RANGE = 48f;
    private static final float LOW_RANGE = 16f;
    // Chain thickness for collision, in model pixels: bounds on what's measured from the
    // geometry, and the value for a bone with none to measure.
    private static final float RADIUS_MIN = 0.5f;
    private static final float RADIUS_MAX = 8f;
    private static final float RADIUS_FALLBACK = 1.5f;
    // Range from the camera that chains test blocks at.
    private static final float BLOCK_RANGE = 16f;
    // Vertical body bob per stride at full walk, in blocks. Vanilla walking moves the legs
    // only, so without this an unbridged bearer's attachments never feel the steps.
    private static final float GAIT_BOB = 0.03f;
    private static final Vector3f AIR = new Vector3f();

    // Render thread scratch.
    private static final Matrix4f FRAME = new Matrix4f();
    private static final Matrix4f SCRATCH = new Matrix4f();
    private static final Matrix4f PARENT_INV = new Matrix4f();
    private static final Quaternionf Q = new Quaternionf();
    private static final Quaternionf Q2 = new Quaternionf();
    private static final Vector3f V = new Vector3f();
    private static final Vector3f V2 = new Vector3f();

    private AttachmentPhysics() {}

    public static void onManifest() {
        STATES.clear();
    }

    /**
     * Steps and applies physics for one attachment instance: sets
     * {@link AttachmentGeo.Bone#physicsRotation} on each chain bone (cleared by the
     * caller after the draw, via the returned list). {@code attachFrame} is the pose
     * matrix the geometry renders under; the bones' own pose fields must already hold
     * every non-physics delta.
     */
    public static List<AttachmentGeo.Bone> apply(LivingEntity entity, AttachmentDef def, int anchorIndex,
                                                 AttachmentGeo geo, Matrix4f attachFrame, float partialTick,
                                                 float limbSwing, float limbSwingAmount,
                                                 List<ChainSim.Capsule> colliders) {
        if (def.physics().isEmpty() || geo == null) return List.of();
        PhysicsQuality quality = quality();
        if (quality == PhysicsQuality.OFF) return List.of();
        Matrix4f root = EntityRootFrames.rootFor(entity);
        if (root == null) return List.of();
        // The attachment frame relative to the entity: origin at its feet, world axes.
        FRAME.set(root).invert().mul(attachFrame);
        boolean gui = root.getScale(V).x > 1.5f;
        // Far bearers hold their animated pose; the gap resets them when they come back.
        if (!gui) {
            float range = quality == PhysicsQuality.FULL ? FULL_RANGE : LOW_RANGE;
            Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            if (entity.distanceToSqr(camera) > range * range) return List.of();
        }

        EntityState entityState = STATES.computeIfAbsent(entity.getId(), k -> new EntityState());
        entityState.lastTouchMs = Util.getMillis();
        if (STATES.size() > 256) evictStale();
        String key = def.id() + "#" + anchorIndex + (gui ? "@gui" : "");
        ChainSetState state = entityState.anchors.get(key);
        if (state == null || state.chains.length != def.physics().size()) {
            state = new ChainSetState(def.physics().size());
            entityState.anchors.put(key, state);
        }

        long now = System.nanoTime();
        float frameDt = state.lastNanos == 0L ? 0f : (now - state.lastNanos) / 1e9f;
        state.lastNanos = now;
        double ex = gui ? 0 : Mth.lerp(partialTick, entity.xOld, entity.getX());
        double ey = gui ? 0 : Mth.lerp(partialTick, entity.yOld, entity.getY());
        double ez = gui ? 0 : Mth.lerp(partialTick, entity.zOld, entity.getZ());
        float dx = (float) (ex - state.entityX), dy = (float) (ey - state.entityY), dz = (float) (ez - state.entityZ);
        state.entityX = ex;
        state.entityY = ey;
        state.entityZ = ez;
        boolean reset = frameDt <= 0f || frameDt > GAP_SECONDS
                || Math.abs(dx) > TELEPORT || Math.abs(dy) > TELEPORT || Math.abs(dz) > TELEPORT;
        // Chains feel a smoothed path: vanilla starts, stops and jumps change speed in a
        // tick, far harder than a body moves. The rest of the motion carries them rigidly.
        if (reset) {
            state.pathVelX = state.pathVelY = state.pathVelZ = 0f;
        } else {
            float ease = 1f - (float) Math.exp(-frameDt / PATH_SMOOTHING);
            state.pathVelX += (dx / frameDt - state.pathVelX) * ease;
            state.pathVelY += (dy / frameDt - state.pathVelY) * ease;
            state.pathVelZ += (dz / frameDt - state.pathVelZ) * ease;
        }

        Vector3f air = gui || quality != PhysicsQuality.FULL ? AIR.zero()
                : AttachmentWind.at(entity.level(), ex, ey, ez, partialTick, AIR);
        float bob = GAIT_BOB * Math.min(1f, limbSwingAmount) * Math.abs(Mth.sin(limbSwing * 0.6662f));
        float size = FRAME.getScale(V).x;
        float sizeFactor = Mth.clamp(1f / (float) Math.sqrt(Math.max(0.05f, size)), 0.6f, 1.8f);
        boolean nearBlocks = !gui && quality == PhysicsQuality.FULL
                && entity.distanceToSqr(Minecraft.getInstance().gameRenderer.getMainCamera().getPosition())
                        < BLOCK_RANGE * BLOCK_RANGE;

        List<AttachmentGeo.Bone> touched = new ArrayList<>();
        for (int c = 0; c < def.physics().size(); c++) {
            AttachmentDef.PhysicsChain chain = def.physics().get(c);
            ChainState chainState = state.chains[c];
            List<AttachmentGeo.Bone> bones = chainState.resolve(chain, geo);
            if (bones == null) continue;
            if (!chainState.captureRest(chain, bones, FRAME)) continue;
            if (reset || !chainState.ready) {
                chainState.settle();
            } else {
                chainState.shift(-state.pathVelX * frameDt, -state.pathVelY * frameDt, -state.pathVelZ * frameDt);
                if (gui) chainState.dry();
                else chainState.wet(entity.level(), ex, ey, ez, frameDt);
                if (nearBlocks && chain.blocks()) chainState.gatherBlocks(entity, ex, ey, ez);
                else chainState.boxCount = 0;
                float speed = chain.scaleWithSize() ? sizeFactor : 1f;
                chainState.simulate(chain, frameDt, bob, speed, anchorIndex * 1.7f + c * 1.1f,
                        colliders, size, air, (float) (chainState.boxX - ex), (float) (chainState.boxY - ey),
                        (float) (chainState.boxZ - ez));
            }
            chainState.lastBob = bob;
            chainState.write(chain, bones, FRAME, touched);
        }
        return touched;
    }

    /**
     * Bench gizmos for one attachment instance: each chain's simulated points (yellow) beside
     * where the animated pose puts them (grey), and each segment's swing limit (orange).
     * {@code pose} must be at the attachment frame {@link #apply} ran under.
     */
    public static void drawGizmos(LivingEntity entity, AttachmentDef def, int anchorIndex, PoseStack pose,
                                  MultiBufferSource buffers) {
        Matrix4f root = EntityRootFrames.rootFor(entity);
        EntityState entityState = STATES.get(entity.getId());
        if (root == null || entityState == null) return;
        boolean gui = root.getScale(new Vector3f()).x > 1.5f;
        ChainSetState state = entityState.anchors.get(def.id() + "#" + anchorIndex + (gui ? "@gui" : ""));
        if (state == null) return;
        // Entity frame -> this attachment's local frame, where the lines draw.
        Matrix4f toLocal = new Matrix4f(root).invert().mul(pose.last().pose()).invert();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        PoseStack.Pose last = pose.last();
        for (int c = 0; c < state.chains.length && c < def.physics().size(); c++) {
            ChainState chain = state.chains[c];
            if (!chain.ready || chain.bones == null) continue;
            AttachmentDef.PhysicsChain config = def.physics().get(c);
            Vector3f simParent = toLocal.transformPosition(new Vector3f(chain.head));
            Vector3f goalParent = new Vector3f(simParent);
            for (int i = 0; i < chain.n; i++) {
                Vector3f sim = toLocal.transformPosition(new Vector3f(chain.pos[i]));
                Vector3f goal = toLocal.transformPosition(new Vector3f(chain.tail[i]));
                BenchGizmos.segment(lines, last, goalParent, goal, BenchGizmos.CHAIN_GOAL);
                BenchGizmos.segment(lines, last, simParent, sim, BenchGizmos.CHAIN);
                Vector3f aim = new Vector3f(goal).sub(goalParent);
                float length = aim.length();
                if (length > 1e-5f) {
                    aim.div(length);
                    float t = chain.n == 1 ? 0f : i / (float) (chain.n - 1);
                    float limit = Mth.clamp(config.limit().at(t), 0f, 180f) * Mth.DEG_TO_RAD;
                    Vector3f center = new Vector3f(simParent).fma(length * Mth.cos(limit), aim);
                    BenchGizmos.circle(lines, last, center, aim, length * Mth.sin(limit), BenchGizmos.CHAIN_LIMIT);
                }
                simParent = sim;
                goalParent = goal;
            }
        }
    }

    /** The configured quality; Reduce Motion caps it at low. */
    private static PhysicsQuality quality() {
        PhysicsQuality quality;
        try {
            quality = TownsteadConfig.ATTACHMENT_PHYSICS.get();
        } catch (Exception e) {
            quality = PhysicsQuality.FULL;
        }
        if (quality == PhysicsQuality.FULL && Accessibility.isReduceMotion()) return PhysicsQuality.LOW;
        return quality;
    }

    private static void evictStale() {
        long now = Util.getMillis();
        STATES.values().removeIf(state -> now - state.lastTouchMs > EVICT_AFTER_MS);
    }

    private static final class EntityState {
        long lastTouchMs;
        final Map<String, ChainSetState> anchors = new HashMap<>();
    }

    private static final class ChainSetState {
        final ChainState[] chains;
        long lastNanos;
        double entityX, entityY, entityZ;
        float pathVelX, pathVelY, pathVelZ;

        ChainSetState(int count) {
            chains = new ChainState[count];
            for (int c = 0; c < count; c++) chains[c] = new ChainState();
        }
    }

    /** A chain's sim, plus what ties it to this bake: its bones, and how to read and write their pose. */
    private static final class ChainState extends ChainSim {
        Object geo;
        List<String> names;
        List<AttachmentGeo.Bone> bones;
        List<AttachmentGeo.Bone>[] between;
        Vector3f[] tailLocal;

        @SuppressWarnings("unchecked")
        @Nullable List<AttachmentGeo.Bone> resolve(AttachmentDef.PhysicsChain chain, AttachmentGeo current) {
            List<String> wanted = chain.effectiveBones();
            if (geo == current && wanted.equals(names)) return bones;
            geo = current;
            names = wanted;
            bones = null;
            ready = false;
            List<AttachmentGeo.Bone> found = new ArrayList<>(names.size());
            for (String name : wanted) {
                AttachmentGeo.Bone bone = current.bone(name);
                if (bone == null) break;
                found.add(bone);
            }
            if (found.isEmpty()) return null;
            // Each listed bone must descend from the one before; keep the valid prefix.
            List<List<AttachmentGeo.Bone>> paths = new ArrayList<>();
            int valid = 1;
            for (int i = 1; i < found.size(); i++) {
                List<AttachmentGeo.Bone> path = pathBetween(found.get(i - 1), found.get(i));
                if (path == null) break;
                paths.add(path);
                valid++;
            }
            bones = List.copyOf(found.subList(0, valid));
            int n = valid;
            between = new List[n];
            between[0] = List.of();
            for (int i = 1; i < n; i++) between[i] = paths.get(i - 1);
            allocate(valid);
            tailLocal = vectors(valid);
            for (int i = 0; i < n; i++) {
                float measured = bones.get(i).halfThickness();
                halfThickness[i] = measured > 0f ? Mth.clamp(measured, RADIUS_MIN, RADIUS_MAX) : RADIUS_FALLBACK;
            }
            return bones;
        }

        /** Bones strictly between {@code ancestor} and {@code bone}, top down; null if unrelated. */
        private static @Nullable List<AttachmentGeo.Bone> pathBetween(AttachmentGeo.Bone ancestor,
                                                                       AttachmentGeo.Bone bone) {
            List<AttachmentGeo.Bone> path = new ArrayList<>();
            for (AttachmentGeo.Bone b = bone.parent(); b != null; b = b.parent()) {
                if (b == ancestor) {
                    java.util.Collections.reverse(path);
                    return path;
                }
                path.add(b);
            }
            return null;
        }

        /** Records the animated pose of every segment this frame; false when it can't be measured. */
        boolean captureRest(AttachmentDef.PhysicsChain chain, List<AttachmentGeo.Bone> chainBones, Matrix4f frame) {
            if (ready) {
                prevHead.set(head);
                for (int i = 0; i < n; i++) {
                    prevTail[i].set(tail[i]);
                    prevPitchAxis[i].set(pitchAxis[i]);
                }
            }
            float restPitch = chain.restPitch() * Mth.DEG_TO_RAD;
            Matrix4f world = parentWorld(chainBones.get(0), frame, SCRATCH);
            for (int i = 0; i < n; i++) {
                AttachmentGeo.Bone bone = chainBones.get(i);
                if (i > 0) {
                    for (AttachmentGeo.Bone mid : between[i]) mid.applyLocal(world, mid.poseRotation(Q));
                }
                bone.applyLocal(world, restRotation(bone, restPitch, Q));
                if (i == 0) world.getTranslation(head);
                else world.getTranslation(tail[i - 1]);
                world.transformDirection(V.set(1f, 0f, 0f), pitchAxis[i]);
                if (pitchAxis[i].lengthSquared() > 1e-8f) pitchAxis[i].normalize();
            }
            // Tail-local offsets: the next pivot through any intermediate bones, or the last bone's tip.
            for (int i = 0; i < n - 1; i++) {
                Matrix4f local = new Matrix4f();
                for (AttachmentGeo.Bone mid : between[i + 1]) mid.applyLocal(local, mid.poseRotation(Q));
                local.transformPosition(chainBones.get(i + 1).pivot(V), tailLocal[i]);
            }
            float[] tip = chainBones.get(n - 1).tip();
            if (tip != null) {
                tailLocal[n - 1].set(tip[0], tip[1], tip[2]);
            } else if (n > 1) {
                tailLocal[n - 1].set(tailLocal[n - 2]).mul(0.5f);
            } else {
                return false;
            }
            world.transformPosition(tailLocal[n - 1], tail[n - 1]);
            if (!ready) {
                prevHead.set(head);
                for (int i = 0; i < n; i++) {
                    prevTail[i].set(tail[i]);
                    prevPitchAxis[i].set(pitchAxis[i]);
                }
            }
            return true;
        }

        /**
         * Turns each chain bone so its segment points where the sim says, root to tip,
         * interpolated between the last two substeps.
         */
        void write(AttachmentDef.PhysicsChain chain, List<AttachmentGeo.Bone> chainBones, Matrix4f frame,
                   List<AttachmentGeo.Bone> touched) {
            float alpha = Mth.clamp(accumulator / DT, 0f, 1f);
            float restPitch = chain.restPitch() * Mth.DEG_TO_RAD;
            Matrix4f parent = parentWorld(chainBones.get(0), frame, new Matrix4f());
            Vector3f restDir = new Vector3f();
            Vector3f wanted = new Vector3f();
            Vector3f current = new Vector3f();
            Quaternionf turn = new Quaternionf();
            for (int i = 0; i < n; i++) {
                AttachmentGeo.Bone bone = chainBones.get(i);
                if (i > 0) {
                    for (AttachmentGeo.Bone mid : between[i]) mid.applyLocal(parent, mid.poseRotation(Q2));
                }
                Vector3f restHead = i == 0 ? head : tail[i - 1];
                tail[i].sub(restHead, restDir);
                if (restDir.lengthSquared() < 1e-10f) return;
                restDir.normalize();
                prevDev[i].slerp(dev[i], alpha, turn).transform(restDir, wanted);
                // Into the parent's space, where the bone's own rotation lives.
                PARENT_INV.set(parent).invert().transformDirection(wanted);
                Quaternionf rest = restRotation(bone, restPitch, new Quaternionf());
                current.set(tailLocal[i]).mul(bone.xScale, bone.yScale, bone.zScale);
                rest.transform(current);
                if (current.lengthSquared() < 1e-10f || wanted.lengthSquared() < 1e-10f) return;
                current.normalize();
                wanted.normalize();
                bone.physicsRotation = new Quaternionf().rotationTo(current, wanted).mul(rest);
                touched.add(bone);
                bone.applyLocal(parent, bone.physicsRotation);
            }
        }

        private static Quaternionf restRotation(AttachmentGeo.Bone bone, float restPitch, Quaternionf dest) {
            return dest.rotationZYX(bone.zRot, bone.yRot, bone.xRot + restPitch);
        }

        /** The frame {@code bone}'s own transform applies under: the attachment frame through its ancestors. */
        private static Matrix4f parentWorld(AttachmentGeo.Bone bone, Matrix4f frame, Matrix4f dest) {
            List<AttachmentGeo.Bone> ancestors = new ArrayList<>();
            for (AttachmentGeo.Bone b = bone.parent(); b != null; b = b.parent()) ancestors.add(b);
            dest.set(frame);
            for (int i = ancestors.size() - 1; i >= 0; i--) {
                AttachmentGeo.Bone a = ancestors.get(i);
                a.applyLocal(dest, a.physicsRotation != null ? a.physicsRotation : a.poseRotation(Q2));
            }
            return dest;
        }

    }
}
