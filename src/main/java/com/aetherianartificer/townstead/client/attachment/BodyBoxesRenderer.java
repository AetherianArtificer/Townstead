package com.aetherianartificer.townstead.client.attachment;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.root.attachment.AttachmentBody;
import com.aetherianartificer.townstead.root.attachment.BodyVolumes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
//? if neoforge {
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
//?} else if forge {
/*import net.minecraftforge.client.event.RenderLevelStageEvent;
*///?}

/**
 * Draws attachment boxes with vanilla's hitboxes (F3+B), which only knows about a dragon's parts. Red can
 * be wounded and green hits the wearer, and both follow the model as it's drawn. White is the pose the server
 * judges hits by, so any gap between white and the rest is visible. Orange stops movement and blue folds
 * away; those two are authored, and sit still.
 */
public final class BodyBoxesRenderer {

    private static long lastErrorLogMs;

    private BodyBoxesRenderer() {}

    public static void onRenderLevel(RenderLevelStageEvent event) {
        try {
            FallingPieces.render(event);
            SeeThroughPass.render(event);
            render(event);
        } catch (Throwable t) {
            // An exception mid-stage can make the level renderer skip later stages entirely.
            long now = System.currentTimeMillis();
            if (now - lastErrorLogMs > 1000L) {
                lastErrorLogMs = now;
                Townstead.LOGGER.warn("[BodyBoxes] render error swallowed: {}", t.toString());
            }
        }
    }

    private static void render(RenderLevelStageEvent event) {
        //? if neoforge {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        //?} else if forge {
        /*if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        *///?}
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || !minecraft.getEntityRenderDispatcher().shouldRenderHitBoxes()) return;
        //? if neoforge {
        float partialTick = minecraft.getTimer().getGameTimeDeltaPartialTick(false);
        //?} else {
        /*float partialTick = minecraft.getFrameTime();
        *///?}
        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living)) continue;
            Vec3 at = living.getPosition(partialTick);
            for (BodyVolumes.Placed placed : BodyVolumes.all(living)) {
                AttachmentBody body = placed.def().body();
                boolean folds = body.volume() != null && AttachmentBody.Volume.FOLD.equals(body.volume().mode());
                LevelRenderer.renderLineBox(pose, lines, placed.box().move(at), folds ? 0.3f : 1f, 0.6f, folds ? 1f : 0.1f, 1f);
            }
            for (var def : com.aetherianartificer.townstead.root.attachment.WornAttachments.of(living)) {
                boolean part = def.body().part() != null;
                for (var box : com.aetherianartificer.townstead.root.attachment.HitShapes.boxes(living, def)) {
                    LevelRenderer.renderLineBox(pose, lines, box.move(at), part ? 1f : 0.2f, part ? 0.15f : 1f, part ? 0.15f : 0.3f, 1f);
                }
                for (var box : com.aetherianartificer.townstead.root.attachment.HitShapes.solved(living, def)) {
                    LevelRenderer.renderLineBox(pose, lines, box.move(at), 1f, 1f, 1f, 0.8f);
                }
            }
        }
        buffers.endBatch(RenderType.lines());
        pose.popPose();
    }
}
