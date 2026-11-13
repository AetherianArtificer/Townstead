package com.aetherianartificer.townstead.client.attachment;

import com.aetherianartificer.townstead.client.attachment.geo.AttachmentGeo;
import com.aetherianartificer.townstead.root.attachment.AttachmentDef;
import com.aetherianartificer.townstead.root.attachment.HitShapes;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Each attachment bone's box as it was actually drawn: every morph, pose, keyframe, life stage and physics
 * swing is already in the matrix the render layer hands over, and on any rig. A crosshair aims at this,
 * and F3+B shows it. It stays on this client: the server judges hits by its own pose ({@link HitShapes}).
 */
public final class LiveBoxes {

    private static final double AIM_RANGE = 8.0;
    private static final long FRESH_TICKS = 2;
    private static final long FORGET_TICKS = 200;

    private static final Map<Integer, Bearer> BEARERS = new HashMap<>();
    private static final Map<String, Map<String, Integer>> GROUPS = new HashMap<>();
    private static final Matrix4f FRAME = new Matrix4f();

    private static final class Bearer {
        long tick;
        final Map<String, float[][]> byDef = new LinkedHashMap<>();
    }

    private LiveBoxes() {}

    /** Whether it's worth measuring this body this frame. */
    public static boolean wanted(LivingEntity entity) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getEntityRenderDispatcher().shouldRenderHitBoxes()) return true;
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        return entity.distanceToSqr(camera) < AIM_RANGE * AIM_RANGE;
    }

    /** Called with the pose at the attachment's frame, with its bones posed for this draw. */
    public static void capture(LivingEntity entity, AttachmentDef def, String geoSha, int anchor, int anchors,
                               AttachmentGeo geo, Matrix4f attachFrame) {
        Matrix4f root = EntityRootFrames.rootFor(entity);
        if (root == null || !wanted(entity)) return;
        // A preview in a GUI isn't the body in the world.
        FRAME.set(root);
        if (FRAME.getScale(new org.joml.Vector3f()).x > 1.5f) return;
        JsonObject json = AttachmentClient.geometryJson(geoSha);
        if (json == null) return;
        Map<String, Integer> groups = GROUPS.computeIfAbsent(geoSha, k -> HitShapes.groups(json));
        if (groups.isEmpty()) return;
        int count = 0;
        for (int group : groups.values()) count = Math.max(count, group + 1);
        float[] flat = new float[count * 6];
        for (int i = 0; i < flat.length; i += 6) {
            flat[i] = flat[i + 1] = flat[i + 2] = Float.MAX_VALUE;
            flat[i + 3] = flat[i + 4] = flat[i + 5] = -Float.MAX_VALUE;
        }
        FRAME.invert().mul(attachFrame);
        geo.measure(FRAME, (bone, minX, minY, minZ, maxX, maxY, maxZ) -> {
            int at = HitShapes.groupOf(groups, bone) * 6;
            flat[at] = Math.min(flat[at], minX);
            flat[at + 1] = Math.min(flat[at + 1], minY);
            flat[at + 2] = Math.min(flat[at + 2], minZ);
            flat[at + 3] = Math.max(flat[at + 3], maxX);
            flat[at + 4] = Math.max(flat[at + 4], maxY);
            flat[at + 5] = Math.max(flat[at + 5], maxZ);
        });
        Bearer bearer = BEARERS.computeIfAbsent(entity.getId(), k -> new Bearer());
        long tick = entity.level().getGameTime();
        // A new tick starts a new picture: what isn't drawn any more isn't there any more.
        if (bearer.tick != tick) {
            if (tick - bearer.tick > 1) bearer.byDef.clear();
            bearer.tick = tick;
        }
        float[][] slots = bearer.byDef.get(def.id());
        if (slots == null || slots.length != anchors) {
            slots = new float[anchors][];
            bearer.byDef.put(def.id(), slots);
        }
        slots[anchor] = flat;
    }

    /** A piece that came off isn't there any more, whatever this tick's earlier picture says. */
    static void forget(LivingEntity entity, AttachmentDef def, int anchor) {
        Bearer bearer = BEARERS.get(entity.getId());
        float[][] slots = bearer == null ? null : bearer.byDef.get(def.id());
        if (slots == null || anchor >= slots.length || slots[anchor] == null) return;
        float[] flat = slots[anchor];
        for (int i = 0; i + 5 < flat.length; i += 6) {
            flat[i] = flat[i + 1] = flat[i + 2] = Float.MAX_VALUE;
            flat[i + 3] = flat[i + 4] = flat[i + 5] = -Float.MAX_VALUE;
        }
    }

    /** What was last drawn for this body, or null when it hasn't been drawn lately. */
    public static Map<String, float[]> measured(LivingEntity entity) {
        Bearer bearer = BEARERS.get(entity.getId());
        if (bearer == null || entity.level().getGameTime() - bearer.tick > FRESH_TICKS) return null;
        Map<String, float[]> out = new LinkedHashMap<>();
        bearer.byDef.forEach((id, slots) -> {
            int length = 0;
            for (float[] slot : slots) {
                if (slot != null) length += slot.length;
            }
            float[] flat = new float[length];
            int at = 0;
            for (float[] slot : slots) {
                if (slot == null) continue;
                System.arraycopy(slot, 0, flat, at, slot.length);
                at += slot.length;
            }
            out.put(id, flat);
        });
        return out;
    }

    /** Each client tick: lets go of bodies not drawn for a while. */
    public static void tick() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            BEARERS.clear();
            return;
        }
        long now = level.getGameTime();
        BEARERS.values().removeIf(bearer -> Math.abs(now - bearer.tick) > FORGET_TICKS);
    }

    public static void clear() {
        BEARERS.clear();
        GROUPS.clear();
    }
}
