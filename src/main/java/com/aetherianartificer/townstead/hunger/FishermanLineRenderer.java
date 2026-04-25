package com.aetherianartificer.townstead.hunger;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.TownsteadConfig;
//? if forge {
/*import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
*///?}
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.phys.Vec3;
//? if forge {
/*import org.joml.Matrix4f;
*///?}
//? if neoforge {
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
//?} else if forge {
/*import net.minecraftforge.client.event.RenderLevelStageEvent;
*///?}

import java.util.Map;

/**
 * Client-side fishing-line renderer for Townstead fishermen. Vanilla
 * FishingHookRenderer skips the line entirely when the hook has no Player
 * owner on the client, which is always the case for our FakePlayer-owned
 * hooks. This event handler reads FishermanHookLinkStore (populated by
 * FishermanHookLinkPayload) and draws a line from the villager's rod hand
 * to the hook for each known link.
 *
 * RenderType.lines() is used instead of lineStrip() so multiple hooks can
 * draw concurrently without strip concatenation; each catenary segment is
 * emitted as a pair of vertices. Catenary math is ported from vanilla's
 * FishingHookRenderer.stringVertex so the sag matches player-owned lines.
 */
public final class FishermanLineRenderer {
    private static final int SEGMENTS = 16;
    private static final double FALLBACK_BOB_AMPLITUDE = 0.055D;
    private static final double FALLBACK_BUBBLE_RADIUS = 0.34D;
    private static final double SYNC_LERP_MILLIS = 50.0D;

    // Anchor offsets for where the fishing line attaches on the villager.
    // Values tuned to land on the visible tip of the fishing-rod item as
    // rendered on a vanilla/MCA villager model. Offsets are relative to the
    // villager body's facing direction:
    //   ANCHOR_SIDE        — +right along the body's right hand side
    //   ANCHOR_FORWARD     — +out along the body's facing direction
    //   ANCHOR_DOWN_FROM_EYE — +down from the villager's eye
    private static final double ANCHOR_SIDE = 0.38D;
    private static final double ANCHOR_FORWARD = 0.9D;
    private static final double ANCHOR_DOWN_FROM_EYE = 0.70D;
    // Bobber-side attach point: blocks above the hook entity position, tuned
    // so the line meets the visible bobber sprite (vanilla's 0.25 attaches
    // at the top of the quad which looks a bit high).
    private static final double BOBBER_END_OFFSET = 0.10D;
    private static final double ROD_END_OVERLAP = 0.06D;

    //? if >=1.21 {
    private static final ResourceLocation BOBBER_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/entity/fishing_hook.png");
    //?} else {
    /*private static final ResourceLocation BOBBER_TEXTURE =
            new ResourceLocation("textures/entity/fishing_hook.png");
    *///?}
    private static final RenderType BOBBER_RENDER_TYPE = RenderType.entityCutout(BOBBER_TEXTURE);
    private static int diagnosticTick;

    private FishermanLineRenderer() {}

    public static void onRenderLevel(RenderLevelStageEvent event) {
        try {
            renderImpl(event);
        } catch (Throwable t) {
            // A thrown exception during AFTER_ENTITIES can cascade and cause
            // the level renderer to skip subsequent stages — making villagers,
            // block entities, weather, and more disappear for that frame and
            // sometimes permanently. Swallow defensively and log once per
            // second so we can diagnose without breaking the whole scene.
            long now = System.currentTimeMillis();
            if (now - lastErrorLogMs > 1000L) {
                lastErrorLogMs = now;
                Townstead.LOGGER.warn("[FishermanLine] render error swallowed: {}", t.toString());
            }
        }
    }

    private static long lastErrorLogMs;

    private static void renderImpl(RenderLevelStageEvent event) {
        //? if neoforge {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        //?} else if forge {
        /*if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        *///?}

        Map<Integer, Integer> links = FishermanHookLinkStore.snapshot();
        boolean debug = TownsteadConfig.DEBUG_VILLAGER_AI.get();
        if (debug && (++diagnosticTick % 120 == 0)) {
            Townstead.LOGGER.info("[FishermanLine] tick links={}", links.size());
        }
        if (links.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        //? if >=1.21 {
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        //?} else {
        /*float partialTick = event.getPartialTick();
        *///?}
        Camera camera = event.getCamera();
        Vec3 camPos = camera.getPosition();
        PoseStack poseStack = event.getPoseStack();
        //? if forge {
        /*renderImmediateForge(poseStack, camPos, partialTick, links, debug);
        return;
        *///?}
        //? if neoforge {
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer consumer = buffers.getBuffer(RenderType.lines());

        int drawn = 0;
        VertexConsumer bobberConsumer = buffers.getBuffer(BOBBER_RENDER_TYPE);
        for (Map.Entry<Integer, Integer> entry : links.entrySet()) {
            int hookId = entry.getKey();
            int villagerId = entry.getValue();
            Entity hookEntity = mc.level.getEntity(hookId);
            FishermanHookLinkStore.SyncedHook synced = FishermanHookLinkStore.syncedHook(hookId);
            Entity villagerEntity = mc.level.getEntity(villagerId);
            if (!(villagerEntity instanceof LivingEntity villager) || !villager.isAlive()) continue;
            boolean hookUsable = hookEntity instanceof FishingHook h && h.isAlive() && h.getPlayerOwner() == null;
            if (!hookUsable) {
                // Forge 1.20.1 kills the fake-player-owned hook on the spawn
                // packet (the keep-alive mixin's @Redirect/@Inject INVOKE
                // targets silently no-op without a refmap), so hookEntity is
                // either null or !isAlive() every frame. The server keeps
                // this cosmetic position refreshed and sends an explicit
                // unlink on reel/abort, so we can draw purely from the synced
                // point without falling back to per-frame eviction (which
                // wiped the synced entry and stopped anything from rendering).
                if (synced != null) {
                    Vec3 hookPos = interpolatedSyncedPosition(synced);
                    renderBobberAt(poseStack, bobberConsumer, camPos, hookPos, 15728880);
                    renderLineTo(poseStack, consumer, camPos, villager, hookPos, partialTick);
                    drawn++;
                }
                continue;
            }
            FishingHook hook = (FishingHook) hookEntity;
            FishermanHookLinkStore.markConfirmed(hookId);

            renderBobberFor(poseStack, bobberConsumer, camPos, hook, partialTick);
            renderLineFor(poseStack, consumer, camPos, villager, hook, partialTick);
            drawn++;
        }

        buffers.endBatch(BOBBER_RENDER_TYPE);
        buffers.endBatch(RenderType.lines());
        if (debug && drawn > 0 && diagnosticTick % 40 == 0) {
            Townstead.LOGGER.info("[FishermanLine] drew {} line(s) this frame", drawn);
        }
        //?}
    }

    //? if forge {
    /*private static void renderImmediateForge(PoseStack poseStack, Vec3 camPos, float partialTick,
                                             Map<Integer, Integer> links, boolean debug) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        RenderSystem.enableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();

        int drawn = 0;
        for (Map.Entry<Integer, Integer> entry : links.entrySet()) {
            int hookId = entry.getKey();
            int villagerId = entry.getValue();
            Entity villagerEntity = mc.level.getEntity(villagerId);
            if (!(villagerEntity instanceof LivingEntity villager) || !villager.isAlive()) continue;

            Vec3 hookPos = null;
            Entity hookEntity = mc.level.getEntity(hookId);
            if (hookEntity instanceof FishingHook hook && hook.isAlive() && hook.getPlayerOwner() == null) {
                hookPos = new Vec3(
                        Mth.lerp((double) partialTick, hook.xo, hook.getX()),
                        Mth.lerp((double) partialTick, hook.yo, hook.getY()),
                        Mth.lerp((double) partialTick, hook.zo, hook.getZ()));
            } else {
                FishermanHookLinkStore.SyncedHook synced = FishermanHookLinkStore.syncedHook(hookId);
                if (synced != null) hookPos = interpolatedSyncedPosition(synced);
            }
            if (hookPos == null) continue;
            hookPos = addFallbackBob(hookId, hookPos, partialTick);

            renderBobberImmediate(poseStack, camPos, hookPos);
            renderBubblesImmediate(poseStack, camPos, hookPos, hookId, partialTick);
            renderLineImmediate(poseStack, camPos, villager, hookPos, partialTick);
            drawn++;
        }

        RenderSystem.enableCull();
        RenderSystem.disableBlend();

        if (debug && drawn > 0 && diagnosticTick % 40 == 0) {
            Townstead.LOGGER.info("[FishermanLine] immediate drew {} line(s)", drawn);
        }
    }

    private static Vec3 addFallbackBob(int hookId, Vec3 hookPos, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return hookPos;
        double phase = (mc.level.getGameTime() + partialTick + hookId * 0.37D) * 0.18D;
        return hookPos.add(0.0D, Math.sin(phase) * FALLBACK_BOB_AMPLITUDE, 0.0D);
    }

    private static void renderBubblesImmediate(PoseStack poseStack, Vec3 camPos, Vec3 hookPos,
                                               int hookId, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        double t = mc.level.getGameTime() + partialTick + hookId * 13.0D;
        // A subtle repeating bubble trail near the bobber. This is cosmetic;
        // the actual bite timing remains server-owned.
        for (int i = 0; i < 3; i++) {
            double age = ((t * 0.075D) + i * 0.33D) % 1.0D;
            double angle = t * 0.09D + i * 2.1D;
            double radius = FALLBACK_BUBBLE_RADIUS * (0.35D + age * 0.65D);
            double x = hookPos.x + Math.cos(angle) * radius;
            double y = hookPos.y + 0.03D + age * 0.16D;
            double z = hookPos.z + Math.sin(angle) * radius;
            renderBubbleImmediate(poseStack, camPos, new Vec3(x, y, z), 0.035F + (float) age * 0.025F);
        }
    }

    private static void renderBubbleImmediate(PoseStack poseStack, Vec3 camPos, Vec3 bubblePos, float scale) {
        poseStack.pushPose();
        try {
            poseStack.translate(bubblePos.x - camPos.x, bubblePos.y - camPos.y, bubblePos.z - camPos.z);
            poseStack.scale(scale, scale, scale);
            poseStack.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
            Matrix4f matrix = poseStack.last().pose();

            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            BufferBuilder builder = Tesselator.getInstance().getBuilder();
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            builder.vertex(matrix, -0.5F, -0.5F, 0.0F).color(170, 220, 255, 115).endVertex();
            builder.vertex(matrix,  0.5F, -0.5F, 0.0F).color(210, 240, 255, 135).endVertex();
            builder.vertex(matrix,  0.5F,  0.5F, 0.0F).color(210, 240, 255, 135).endVertex();
            builder.vertex(matrix, -0.5F,  0.5F, 0.0F).color(170, 220, 255, 115).endVertex();
            BufferUploader.drawWithShader(builder.end());
        } finally {
            poseStack.popPose();
        }
    }

    private static void renderBobberImmediate(PoseStack poseStack, Vec3 camPos, Vec3 hookPos) {
        poseStack.pushPose();
        try {
            poseStack.translate(hookPos.x - camPos.x, hookPos.y - camPos.y, hookPos.z - camPos.z);
            poseStack.scale(0.5F, 0.5F, 0.5F);
            poseStack.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
            Matrix4f matrix = poseStack.last().pose();

            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            RenderSystem.setShaderTexture(0, BOBBER_TEXTURE);
            BufferBuilder builder = Tesselator.getInstance().getBuilder();
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            builder.vertex(matrix, -0.5F, -0.5F, 0.0F).uv(0.0F, 1.0F).color(255, 255, 255, 255).endVertex();
            builder.vertex(matrix,  0.5F, -0.5F, 0.0F).uv(1.0F, 1.0F).color(255, 255, 255, 255).endVertex();
            builder.vertex(matrix,  0.5F,  0.5F, 0.0F).uv(1.0F, 0.0F).color(255, 255, 255, 255).endVertex();
            builder.vertex(matrix, -0.5F,  0.5F, 0.0F).uv(0.0F, 0.0F).color(255, 255, 255, 255).endVertex();
            BufferUploader.drawWithShader(builder.end());
        } finally {
            poseStack.popPose();
        }
    }

    private static void renderLineImmediate(PoseStack poseStack, Vec3 camPos, LivingEntity villager,
                                            Vec3 hookPos, float partialTick) {
        Vec3 handPos = rodHandWorldPos(villager, partialTick);
        double hookX = hookPos.x;
        double hookY = hookPos.y + BOBBER_END_OFFSET;
        double hookZ = hookPos.z;

        Vec3 endPos = lineEndWithRodOverlap(new Vec3(hookX, hookY, hookZ), handPos);
        float dx = (float) (endPos.x - hookX);
        float dy = (float) (endPos.y - hookY);
        float dz = (float) (endPos.z - hookZ);

        poseStack.pushPose();
        try {
            poseStack.translate(hookX - camPos.x, hookY - camPos.y, hookZ - camPos.z);
            Matrix4f matrix = poseStack.last().pose();

            BufferBuilder builder = Tesselator.getInstance().getBuilder();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            float prevX = 0F, prevY = 0F, prevZ = 0F;
            for (int k = 1; k <= SEGMENTS; k++) {
                float t = k / (float) SEGMENTS;
                float x = dx * t;
                float y = dy * (t * t + t) * 0.5F;
                float z = dz * t;
                emitLineQuad(builder, matrix, prevX, prevY, prevZ, x, y, z, camPos,
                        new Vec3(hookX, hookY, hookZ));
                prevX = x;
                prevY = y;
                prevZ = z;
            }
            BufferUploader.drawWithShader(builder.end());
        } finally {
            poseStack.popPose();
        }
    }

    private static void emitLineQuad(BufferBuilder builder, Matrix4f matrix,
                                     float x0, float y0, float z0,
                                     float x1, float y1, float z1,
                                     Vec3 camPos, Vec3 hookPos) {
        double wx0 = hookPos.x + x0;
        double wy0 = hookPos.y + y0;
        double wz0 = hookPos.z + z0;
        double wx1 = hookPos.x + x1;
        double wy1 = hookPos.y + y1;
        double wz1 = hookPos.z + z1;

        Vec3 seg = new Vec3(wx1 - wx0, wy1 - wy0, wz1 - wz0);
        Vec3 view = new Vec3((wx0 + wx1) * 0.5D - camPos.x,
                (wy0 + wy1) * 0.5D - camPos.y,
                (wz0 + wz1) * 0.5D - camPos.z);
        Vec3 side = seg.cross(view);
        if (side.lengthSqr() < 1.0e-8D) {
            side = new Vec3(0.0D, 1.0D, 0.0D);
        } else {
            side = side.normalize().scale(0.0125D);
        }

        float ox = (float) side.x;
        float oy = (float) side.y;
        float oz = (float) side.z;
        int r = 18, g = 18, b = 18, a = 255;
        builder.vertex(matrix, x0 - ox, y0 - oy, z0 - oz).color(r, g, b, a).endVertex();
        builder.vertex(matrix, x0 + ox, y0 + oy, z0 + oz).color(r, g, b, a).endVertex();
        builder.vertex(matrix, x1 + ox, y1 + oy, z1 + oz).color(r, g, b, a).endVertex();
        builder.vertex(matrix, x1 - ox, y1 - oy, z1 - oz).color(r, g, b, a).endVertex();
    }
    *///?}

    private static Vec3 interpolatedSyncedPosition(FishermanHookLinkStore.SyncedHook synced) {
        double t = (System.currentTimeMillis() - synced.updatedAtMillis()) / SYNC_LERP_MILLIS;
        if (t < 0.0D) t = 0.0D;
        if (t > 1.0D) t = 1.0D;
        double x = Mth.lerp(t, synced.previousX(), synced.x());
        double y = Mth.lerp(t, synced.previousY(), synced.y());
        double z = Mth.lerp(t, synced.previousZ(), synced.z());
        return new Vec3(x, y, z);
    }

    /**
     * Render a billboarded bobber quad at the hook's interpolated position.
     * Ported from vanilla FishingHookRenderer.render (1.21.1) — single quad,
     * camera-aligned via entityRenderDispatcher.cameraOrientation, 0.5-scale.
     * Light is resolved from the hook's current block position.
     */
    private static void renderBobberFor(PoseStack poseStack, VertexConsumer consumer,
                                        Vec3 camPos, FishingHook hook, float partialTick) {
        double hookX = Mth.lerp((double) partialTick, hook.xo, hook.getX());
        double hookY = Mth.lerp((double) partialTick, hook.yo, hook.getY());
        double hookZ = Mth.lerp((double) partialTick, hook.zo, hook.getZ());

        Minecraft mc = Minecraft.getInstance();
        int packedLight = mc.getEntityRenderDispatcher().getPackedLightCoords(hook, partialTick);
        renderBobberAt(poseStack, consumer, camPos, new Vec3(hookX, hookY, hookZ), packedLight);
    }

    private static void renderBobberAt(PoseStack poseStack, VertexConsumer consumer,
                                       Vec3 camPos, Vec3 hookPos, int packedLight) {
        poseStack.pushPose();
        try {
            poseStack.translate(hookPos.x - camPos.x, hookPos.y - camPos.y, hookPos.z - camPos.z);
            poseStack.scale(0.5F, 0.5F, 0.5F);
            poseStack.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
            PoseStack.Pose pose = poseStack.last();

            emitBobberVertex(consumer, pose, packedLight, -0.5F, -0.5F, 0, 1);
            emitBobberVertex(consumer, pose, packedLight,  0.5F, -0.5F, 1, 1);
            emitBobberVertex(consumer, pose, packedLight,  0.5F,  0.5F, 1, 0);
            emitBobberVertex(consumer, pose, packedLight, -0.5F,  0.5F, 0, 0);
        } finally {
            // Always pop. Without this, an exception between push and pop
            // leaves the pose stack unbalanced, and vanilla's end-of-frame
            // checkPoseStack() crashes with "Pose stack not empty".
            poseStack.popPose();
        }
    }

    private static void emitBobberVertex(VertexConsumer consumer, PoseStack.Pose pose,
                                         int packedLight, float x, float y, int u, int v) {
        //? if >=1.21 {
        consumer.addVertex(pose, x, y, 0.0F)
                .setColor(-1)
                .setUv((float) u, (float) v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
        //?} else {
        /*consumer.vertex(pose.pose(), x, y, 0.0F)
                .color(255, 255, 255, 255)
                .uv((float) u, (float) v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(packedLight)
                .normal(pose.normal(), 0.0F, 1.0F, 0.0F)
                .endVertex();
        *///?}
    }

    private static void renderLineFor(PoseStack poseStack, VertexConsumer consumer,
                                      Vec3 camPos, LivingEntity villager, FishingHook hook,
                                      float partialTick) {
        double hookX = Mth.lerp((double) partialTick, hook.xo, hook.getX());
        double hookY = Mth.lerp((double) partialTick, hook.yo, hook.getY());
        double hookZ = Mth.lerp((double) partialTick, hook.zo, hook.getZ());
        renderLineTo(poseStack, consumer, camPos, villager, new Vec3(hookX, hookY, hookZ), partialTick);
    }

    private static void renderLineTo(PoseStack poseStack, VertexConsumer consumer,
                                     Vec3 camPos, LivingEntity villager, Vec3 hookPos,
                                     float partialTick) {
        Vec3 handPos = rodHandWorldPos(villager, partialTick);
        double hookX = hookPos.x;
        double hookY = hookPos.y + BOBBER_END_OFFSET;
        double hookZ = hookPos.z;

        Vec3 endPos = lineEndWithRodOverlap(new Vec3(hookX, hookY, hookZ), handPos);
        float dx = (float) (endPos.x - hookX);
        float dy = (float) (endPos.y - hookY);
        float dz = (float) (endPos.z - hookZ);

        poseStack.pushPose();
        try {
            poseStack.translate(hookX - camPos.x, hookY - camPos.y, hookZ - camPos.z);
            PoseStack.Pose pose = poseStack.last();
            // Catenary from local (0,0,0) (bobber attach point, already translated
            // by bobberEndOffset above) to local (dx, dy, dz) (hand attach point).
            // y = dy * (t² + t) / 2 gives a concave-up curve that sags below the
            // straight line by up to dy * 0.125 near t = 0.5 — visually reads as
            // a droopy fishing line when dy > 0 (hand above bobber).
            float prevX = 0F, prevY = 0F, prevZ = 0F;
            for (int k = 1; k <= SEGMENTS; k++) {
                float t = k / (float) SEGMENTS;
                float x = dx * t;
                float y = dy * (t * t + t) * 0.5F;
                float z = dz * t;
                emitLineSegment(consumer, pose, prevX, prevY, prevZ, x, y, z);
                prevX = x;
                prevY = y;
                prevZ = z;
            }
        } finally {
            poseStack.popPose();
        }
    }

    private static Vec3 lineEndWithRodOverlap(Vec3 hookAttachPos, Vec3 handPos) {
        Vec3 towardHand = handPos.subtract(hookAttachPos);
        if (towardHand.lengthSqr() < 1.0e-6D) {
            return handPos;
        }
        return handPos.add(towardHand.normalize().scale(ROD_END_OVERLAP));
    }

    /** World-space position where the fishing line attaches on the villager. */
    private static Vec3 rodHandWorldPos(LivingEntity villager, float partialTick) {
        float bodyRot = Mth.lerp(partialTick, villager.yBodyRotO, villager.yBodyRot) * ((float) Math.PI / 180F);
        double sin = Mth.sin(bodyRot);
        double cos = Mth.cos(bodyRot);

        double x = Mth.lerp((double) partialTick, villager.xo, villager.getX()) - cos * ANCHOR_SIDE - sin * ANCHOR_FORWARD;
        //? if >=1.21 {
        double y = villager.yo + villager.getEyeHeight() + (villager.getY() - villager.yo) * (double) partialTick - ANCHOR_DOWN_FROM_EYE;
        //?} else {
        /*double y = villager.yo + (double) villager.getEyeHeight() + (villager.getY() - villager.yo) * (double) partialTick - ANCHOR_DOWN_FROM_EYE;
        *///?}
        double z = Mth.lerp((double) partialTick, villager.zo, villager.getZ()) - sin * ANCHOR_SIDE + cos * ANCHOR_FORWARD;
        return new Vec3(x, y, z);
    }

    private static void emitLineSegment(VertexConsumer consumer, PoseStack.Pose pose,
                                        float x0, float y0, float z0,
                                        float x1, float y1, float z1) {
        float nx = x1 - x0;
        float ny = y1 - y0;
        float nz = z1 - z0;
        float len = Mth.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1.0e-6F) len = 1.0e-6F;
        nx /= len;
        ny /= len;
        nz /= len;
        //? if >=1.21 {
        consumer.addVertex(pose.pose(), x0, y0, z0)
                .setColor(0, 0, 0, 255)
                .setNormal(pose, nx, ny, nz);
        consumer.addVertex(pose.pose(), x1, y1, z1)
                .setColor(0, 0, 0, 255)
                .setNormal(pose, nx, ny, nz);
        //?} else {
        /*consumer.vertex(pose.pose(), x0, y0, z0)
                .color(0, 0, 0, 255)
                .normal(pose.normal(), nx, ny, nz)
                .endVertex();
        consumer.vertex(pose.pose(), x1, y1, z1)
                .color(0, 0, 0, 255)
                .normal(pose.normal(), nx, ny, nz)
                .endVertex();
        *///?}
    }
}
