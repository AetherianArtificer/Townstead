package com.aetherianartificer.townstead.client.attachment;

import com.aetherianartificer.townstead.client.attachment.geo.AttachmentGeo;
import com.aetherianartificer.townstead.root.attachment.AttachmentDef;
import com.aetherianartificer.townstead.root.attachment.BodyPose;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
//? if neoforge {
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
//?} else if forge {
/*import net.minecraftforge.client.event.RenderLevelStageEvent;
*///?}

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Pieces of attachments falling off a body, on this client only: nothing to save and nothing for a server
 * to run. The next time a body draws a piece that came off, its mesh is copied exactly as drawn, with
 * whatever pose and swing it had, and the copy falls and lies on the ground until it fades, as long as a
 * dropped item would by default. A body nobody here is drawing leaves no piece, and nobody here is looking
 * at it either.
 */
public final class FallingPieces {

    private static final int WANT_TICKS = 40;
    private static final int FADE_TICKS = 100;
    private static final int MAX_PIECES = 64;
    private static final double DRAW_RANGE_SQR = 96.0 * 96.0;
    private static final double GRAVITY = 0.04, DRAG = 0.98;
    private static final Random RANDOM = new Random();

    // Asked to fall and not drawn yet, then drawn and gone until the body's own values catch up.
    private static final Map<String, Long> WANTED = new HashMap<>();
    private static final Map<String, Long> GONE = new HashMap<>();
    private static final List<Piece> PIECES = new ArrayList<>();

    private FallingPieces() {}

    private static final class Piece {
        float[][] vertices;
        RenderType type;
        boolean solid;
        @Nullable ResourceLocation emissive;
        List<com.aetherianartificer.townstead.compat.mess.MessClientLink.Mark> marks = List.of();
        int tint, opacity;
        double x, y, z, lastX, lastY, lastZ, vx, vy, vz;
        final Quaternionf spin = new Quaternionf();
        final Quaternionf lastSpin = new Quaternionf();
        Vector3f axis;
        float rate;
        double half;
        int age, life;
    }

    private static String key(int entityId, String attachment, int piece) {
        return entityId + "/" + attachment + "/" + piece;
    }

    private static long now() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0L : mc.level.getGameTime();
    }

    public static void want(int entityId, String attachment, int piece) {
        WANTED.put(key(entityId, attachment, piece), now() + WANT_TICKS);
    }

    public static boolean wants(int entityId, String attachment, int piece) {
        return !WANTED.isEmpty() && WANTED.containsKey(key(entityId, attachment, piece));
    }

    /** Whether any piece of this attachment is still to fall: a whole lost one is otherwise not drawn at all. */
    public static boolean wantsAny(int entityId, String attachment) {
        if (WANTED.isEmpty()) return false;
        String prefix = entityId + "/" + attachment + "/";
        for (String key : WANTED.keySet()) {
            if (key.startsWith(prefix)) return true;
        }
        return false;
    }

    public static boolean gone(int entityId, String attachment, int piece) {
        return !GONE.isEmpty() && GONE.containsKey(key(entityId, attachment, piece));
    }

    /** Copies a piece as it was just drawn and lets it fall. {@code geo} still holds this frame's pose. */
    static void capture(LivingEntity entity, AttachmentDef def, int anchor, int piece, List<String> roots, int split,
                        AttachmentGeo geo, RenderType type, boolean solid, int opacity, int tint, @Nullable ResourceLocation emissive,
                        List<com.aetherianartificer.townstead.compat.mess.MessClientLink.Mark> marks) {
        String key = key(entity.getId(), def.id(), piece);
        WANTED.remove(key);
        GONE.put(key, now() + WANT_TICKS);
        // The oldest makes way: the one just cut off is what someone is looking at.
        if (PIECES.size() >= MAX_PIECES) letGo(PIECES.remove(0));
        Matrix4f frame = BodyPose.anchorFrame(entity, def, anchor);
        if (frame == null) return;
        VertexCapture capture = new VertexCapture();
        // Just this branch: the rest of the model stays on the body.
        List<AttachmentGeo.Bone> others = new ArrayList<>();
        for (int r = 0; r < roots.size(); r++) {
            AttachmentGeo.Bone bone = r == split ? null : geo.bone(roots.get(r));
            if (bone != null && bone.visible) {
                bone.visible = false;
                others.add(bone);
            }
        }
        geo.render(new PoseStack(), capture, 0, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
        for (AttachmentGeo.Bone bone : others) bone.visible = true;
        List<float[]> local = capture.vertices();
        if (local.isEmpty()) return;

        // Into world axes, around the piece's own middle, so it turns about that as it falls.
        Matrix3f normals = new Matrix3f(frame).invert().transpose();
        float[][] baked = new float[local.size()][];
        float loX = Float.MAX_VALUE, loY = Float.MAX_VALUE, loZ = Float.MAX_VALUE;
        float hiX = -Float.MAX_VALUE, hiY = -Float.MAX_VALUE, hiZ = -Float.MAX_VALUE;
        Vector3f at = new Vector3f();
        Vector3f normal = new Vector3f();
        for (int i = 0; i < baked.length; i++) {
            float[] v = local.get(i);
            frame.transformPosition(at.set(v[0], v[1], v[2]));
            normals.transform(normal.set(v[5], v[6], v[7]));
            if (normal.lengthSquared() > 1e-8f) normal.normalize();
            baked[i] = new float[]{at.x, at.y, at.z, v[3], v[4], normal.x, normal.y, normal.z};
            loX = Math.min(loX, at.x);
            loY = Math.min(loY, at.y);
            loZ = Math.min(loZ, at.z);
            hiX = Math.max(hiX, at.x);
            hiY = Math.max(hiY, at.y);
            hiZ = Math.max(hiZ, at.z);
        }
        float midX = (loX + hiX) / 2f, midY = (loY + hiY) / 2f, midZ = (loZ + hiZ) / 2f;
        for (float[] v : baked) {
            v[0] -= midX;
            v[1] -= midY;
            v[2] -= midZ;
        }
        Piece out = new Piece();
        out.vertices = baked;
        out.type = type;
        out.solid = solid;
        out.opacity = opacity;
        out.emissive = emissive;
        out.marks = marks;
        out.tint = tint & 0xFFFFFF;
        var rules = def.body().part();
        out.life = (rules == null ? com.aetherianartificer.townstead.root.attachment.AttachmentBody.Part.REMAINS : rules.remainsSeconds()) * 20;
        out.x = out.lastX = entity.getX() + midX;
        out.y = out.lastY = entity.getY() + midY;
        out.z = out.lastZ = entity.getZ() + midZ;
        // It rests on its thinnest side, near enough.
        out.half = Math.max(0.04, Math.min(hiX - loX, Math.min(hiY - loY, hiZ - loZ)) / 2.0);
        Vec3 away = new Vec3(midX, 0.0, midZ);
        away = away.lengthSqr() < 1e-4 ? new Vec3(RANDOM.nextDouble() - 0.5, 0.0, RANDOM.nextDouble() - 0.5) : away;
        away = away.normalize().scale(0.08 + RANDOM.nextDouble() * 0.06);
        Vec3 carried = entity.getDeltaMovement();
        out.vx = carried.x + away.x;
        out.vy = Math.max(carried.y, 0.0) + 0.12 + RANDOM.nextDouble() * 0.06;
        out.vz = carried.z + away.z;
        out.axis = new Vector3f(RANDOM.nextFloat() - 0.5f, RANDOM.nextFloat() - 0.5f, RANDOM.nextFloat() - 0.5f);
        if (out.axis.lengthSquared() < 1e-4f) out.axis.set(1f, 0f, 0f);
        out.axis.normalize();
        out.rate = 0.18f + RANDOM.nextFloat() * 0.3f;
        PIECES.add(out);
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            WANTED.clear();
            GONE.clear();
            PIECES.forEach(FallingPieces::letGo);
            PIECES.clear();
            return;
        }
        if (mc.isPaused()) return;
        long now = mc.level.getGameTime();
        WANTED.values().removeIf(until -> until <= now);
        GONE.values().removeIf(until -> until <= now);
        for (Iterator<Piece> it = PIECES.iterator(); it.hasNext(); ) {
            Piece piece = it.next();
            if (++piece.age >= piece.life) {
                letGo(piece);
                it.remove();
                continue;
            }
            piece.lastX = piece.x;
            piece.lastY = piece.y;
            piece.lastZ = piece.z;
            piece.lastSpin.set(piece.spin);
            piece.vy = (piece.vy - GRAVITY) * DRAG;
            piece.vx *= DRAG;
            piece.vz *= DRAG;
            AABB box = new AABB(piece.x - piece.half, piece.y - piece.half, piece.z - piece.half,
                    piece.x + piece.half, piece.y + piece.half, piece.z + piece.half);
            Vec3 wanted = new Vec3(piece.vx, piece.vy, piece.vz);
            Vec3 moved = Entity.collideBoundingBox(null, wanted, box, mc.level, List.of());
            piece.x += moved.x;
            piece.y += moved.y;
            piece.z += moved.z;
            if (moved.x != wanted.x) piece.vx = -piece.vx * 0.3;
            if (moved.z != wanted.z) piece.vz = -piece.vz * 0.3;
            if (moved.y != wanted.y) {
                boolean landed = wanted.y < 0.0;
                piece.vy = landed && piece.vy < -0.2 ? -piece.vy * 0.25 : 0.0;
                if (landed) {
                    piece.vx *= 0.6;
                    piece.vz *= 0.6;
                    piece.rate *= 0.55f;
                }
            }
            if (piece.rate > 0.005f) piece.spin.rotateAxis(piece.rate, piece.axis);
        }
    }

    public static void render(RenderLevelStageEvent event) {
        if (PIECES.isEmpty()) return;
        //? if neoforge {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        //?} else if forge {
        /*if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        *///?}
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        //? if neoforge {
        float partialTick = mc.getTimer().getGameTimeDeltaPartialTick(false);
        //?} else {
        /*float partialTick = mc.getFrameTime();
        *///?}
        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Quaternionf spin = new Quaternionf();
        for (Piece piece : PIECES) {
            double x = piece.lastX + (piece.x - piece.lastX) * partialTick;
            double y = piece.lastY + (piece.y - piece.lastY) * partialTick;
            double z = piece.lastZ + (piece.z - piece.lastZ) * partialTick;
            if (camera.distanceToSqr(x, y, z) > DRAW_RANGE_SQR) continue;
            piece.lastSpin.nlerp(piece.spin, partialTick, spin);
            pose.pushPose();
            pose.translate(x - camera.x, y - camera.y, z - camera.z);
            pose.mulPose(spin);
            int left = piece.life - piece.age;
            int alpha = piece.opacity;
            if (left < FADE_TICKS) {
                // A solid piece draws as it did on the body, and shrinks away: shader packs light a
                // see-through draw in a later pass, so fading it would change its colour.
                float remaining = left / (float) FADE_TICKS;
                if (piece.solid) pose.scale(remaining, remaining, remaining);
                else alpha = Math.max(1, Math.round(alpha * remaining));
            }
            int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(x, y + piece.half, z));
            draw(piece, pose, buffers.getBuffer(piece.type), light, alpha, piece.tint);
            buffers.endBatch(piece.type);
            if (piece.emissive != null) {
                RenderType glow = RenderType.entityTranslucentEmissive(piece.emissive);
                draw(piece, pose, buffers.getBuffer(glow), light, alpha, 0xFFFFFF);
                buffers.endBatch(glow);
            }
            // What had landed on it comes along.
            for (var mark : piece.marks) {
                RenderType marked = RenderType.entityTranslucent(mark.id());
                draw(piece, pose, buffers.getBuffer(marked), light, Math.min(alpha, mark.argb() >>> 24), mark.argb() & 0xFFFFFF);
                buffers.endBatch(marked);
            }
            pose.popPose();
        }
    }

    private static void letGo(Piece piece) {
        for (var mark : piece.marks) Minecraft.getInstance().getTextureManager().release(mark.id());
    }

    private static void draw(Piece piece, PoseStack pose, VertexConsumer buffer, int light, int alpha, int tint) {
        Matrix4f matrix = pose.last().pose();
        Matrix3f rotation = pose.last().normal();
        Vector3f at = new Vector3f();
        Vector3f normal = new Vector3f();
        for (float[] v : piece.vertices) {
            matrix.transformPosition(at.set(v[0], v[1], v[2]));
            rotation.transform(normal.set(v[5], v[6], v[7]));
            //? if neoforge {
            buffer.addVertex(at.x, at.y, at.z, (alpha << 24) | tint, v[3], v[4], OverlayTexture.NO_OVERLAY, light,
                    normal.x, normal.y, normal.z);
            //?} else {
            /*buffer.vertex(at.x, at.y, at.z, ((tint >> 16) & 0xFF) / 255f, ((tint >> 8) & 0xFF) / 255f, (tint & 0xFF) / 255f,
                    alpha / 255f, v[3], v[4], OverlayTexture.NO_OVERLAY, light, normal.x, normal.y, normal.z);
            *///?}
        }
    }
}
