package com.aetherianartificer.townstead.client.devlink;

import com.aetherianartificer.townstead.devlink.BenchAnchors;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Records what the attachment layer actually computed for one entity on one frame: each channel
 * bone, each attachment point, and each attachment instance, as matrices relative to the layer's
 * entry pose (Java model space, in blocks: multiply translations by 16 for model pixels).
 *
 * <p>The Benchstead plugin compares its own placement against these, so the game stays the oracle
 * for coordinate conventions. Capture is opt-in per entity: the HTTP thread registers interest,
 * the next frame that draws the entity fills it, and nothing is recorded otherwise.</p>
 */
public final class BenchAnchorCapture implements BenchAnchors.Source {

    public static final BenchAnchorCapture INSTANCE = new BenchAnchorCapture();

    private static final Map<Integer, Boolean> WANTED = new ConcurrentHashMap<>();
    private static final Map<Integer, JsonObject> DONE = new ConcurrentHashMap<>();

    private BenchAnchorCapture() {}

    public static boolean wants(int entityId) {
        return !WANTED.isEmpty() && WANTED.containsKey(entityId);
    }

    @Override
    public @Nullable JsonObject capture(int entityId, long timeoutMs) throws InterruptedException {
        DONE.remove(entityId);
        WANTED.put(entityId, Boolean.TRUE);
        long deadline = System.currentTimeMillis() + timeoutMs;
        try {
            synchronized (DONE) {
                while (!DONE.containsKey(entityId)) {
                    long left = deadline - System.currentTimeMillis();
                    if (left <= 0) return null;
                    DONE.wait(left);
                }
            }
            return DONE.remove(entityId);
        } finally {
            WANTED.remove(entityId);
        }
    }

    /** One frame's capture, built by the render layer and published by {@link #finish}. */
    public static final class Frame {
        private final int entityId;
        private final Matrix4f inverseEntry;
        private final JsonObject json = new JsonObject();
        private final JsonObject bones = new JsonObject();
        private final JsonArray points = new JsonArray();
        private final JsonArray attachments = new JsonArray();

        private Frame(LivingEntity entity, Matrix4f entry, String rig) {
            this.entityId = entity.getId();
            this.inverseEntry = new Matrix4f(entry).invert();
            json.addProperty("entity", entityId);
            json.addProperty("rig", rig);
            json.addProperty("space", "java_model");
            json.addProperty("units", "blocks");
            json.addProperty("layout", "column_major");
            json.add("bones", bones);
            json.add("points", points);
            json.add("attachments", attachments);
        }

        public void bone(String channel, ModelPart part, Matrix4f matrix) {
            JsonObject bone = new JsonObject();
            JsonArray rest = new JsonArray();
            rest.add(part.x);
            rest.add(part.y);
            rest.add(part.z);
            bone.add("position", rest);
            JsonArray rotation = new JsonArray();
            rotation.add(Math.toDegrees(part.xRot));
            rotation.add(Math.toDegrees(part.yRot));
            rotation.add(Math.toDegrees(part.zRot));
            bone.add("rotation", rotation);
            bone.add("matrix", relative(matrix));
            bones.add(channel, bone);
        }

        public void point(String id, String bone, Matrix4f matrix) {
            JsonObject point = new JsonObject();
            point.addProperty("id", id);
            point.addProperty("bone", bone);
            point.add("matrix", relative(matrix));
            points.add(point);
        }

        public void attachment(String id, int anchorIndex, String bone, boolean mirror, boolean posed,
                               Matrix4f boneMatrix, Matrix4f matrix) {
            JsonObject attachment = new JsonObject();
            attachment.addProperty("id", id);
            attachment.addProperty("anchor", anchorIndex);
            attachment.addProperty("bone", bone);
            attachment.addProperty("mirror", mirror);
            // A pose, clip or physics swing moves the attachment off its authored rest frame;
            // the plugin skips posed instances when it checks placement.
            attachment.addProperty("posed", posed);
            attachment.add("boneMatrix", relative(boneMatrix));
            attachment.add("matrix", relative(matrix));
            attachments.add(attachment);
        }

        private JsonArray relative(Matrix4f matrix) {
            Matrix4f local = new Matrix4f(inverseEntry).mul(matrix);
            float[] values = new float[16];
            local.get(values);
            JsonArray array = new JsonArray();
            for (float value : values) array.add(value);
            return array;
        }
    }

    public static Frame begin(LivingEntity entity, Matrix4f entry, String rig) {
        return new Frame(entity, entry, rig);
    }

    public static void finish(Frame frame) {
        synchronized (DONE) {
            DONE.put(frame.entityId, frame.json);
            WANTED.remove(frame.entityId);
            DONE.notifyAll();
        }
    }
}
