package com.aetherianartificer.townstead.hunger;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.TownsteadConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
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
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;

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
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer consumer = buffers.getBuffer(RenderType.lines());

        int drawn = 0;
        VertexConsumer bobberConsumer = buffers.getBuffer(BOBBER_RENDER_TYPE);
        for (Map.Entry<Integer, Integer> entry : links.entrySet()) {
            int hookId = entry.getKey();
            int villagerId = entry.getValue();
            Entity hookEntity = mc.level.getEntity(hookId);
            if (hookEntity == null) continue; // race: link arrived before spawn
            if (!(hookEntity instanceof FishingHook hook) || !hook.isAlive()) {
                FishermanHookLinkStore.unlink(hookId);
                continue;
            }
            Entity villagerEntity = mc.level.getEntity(villagerId);
            if (!(villagerEntity instanceof LivingEntity villager) || !villager.isAlive()) continue;
            if (hook.getPlayerOwner() != null) continue;

            renderBobberFor(poseStack, bobberConsumer, camPos, hook, partialTick);
            renderLineFor(poseStack, consumer, camPos, villager, hook, partialTick);
            drawn++;
        }

        buffers.endBatch(BOBBER_RENDER_TYPE);
        buffers.endBatch(RenderType.lines());
        if (debug && drawn > 0 && diagnosticTick % 40 == 0) {
            Townstead.LOGGER.info("[FishermanLine] drew {} line(s) this frame", drawn);
        }
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

        poseStack.pushPose();
        poseStack.translate(hookX - camPos.x, hookY - camPos.y, hookZ - camPos.z);
        poseStack.scale(0.5F, 0.5F, 0.5F);
        poseStack.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        PoseStack.Pose pose = poseStack.last();

        emitBobberVertex(consumer, pose, packedLight, -0.5F, -0.5F, 0, 1);
        emitBobberVertex(consumer, pose, packedLight,  0.5F, -0.5F, 1, 1);
        emitBobberVertex(consumer, pose, packedLight,  0.5F,  0.5F, 1, 0);
        emitBobberVertex(consumer, pose, packedLight, -0.5F,  0.5F, 0, 0);

        poseStack.popPose();
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
        Vec3 handPos = rodHandWorldPos(villager, partialTick);
        double hookX = Mth.lerp((double) partialTick, hook.xo, hook.getX());
        double hookY = Mth.lerp((double) partialTick, hook.yo, hook.getY()) + BOBBER_END_OFFSET;
        double hookZ = Mth.lerp((double) partialTick, hook.zo, hook.getZ());

        float dx = (float) (handPos.x - hookX);
        float dy = (float) (handPos.y - hookY);
        float dz = (float) (handPos.z - hookZ);

        poseStack.pushPose();
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
        poseStack.popPose();
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
