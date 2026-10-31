package com.aetherianartificer.townstead.client.building;

import com.aetherianartificer.townstead.Townstead;
//? if forge {
/*import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
*///?}
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
//? if neoforge {
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
//?} else if forge {
/*import net.minecraftforge.client.event.RenderLevelStageEvent;
*///?}

/** Outlines the gap the server last reported in an enclosure, for a few seconds. */
public final class EnclosureGapRenderer {
    private static final long SHOW_MILLIS = 6000L;
    private static final float RED = 1.0F;
    private static final float GREEN = 0.45F;
    private static final float BLUE = 0.1F;

    private static volatile BlockPos gap;
    private static volatile long until;
    private static long lastErrorLogMs;

    private EnclosureGapRenderer() {}

    public static void show(BlockPos pos) {
        gap = pos;
        until = System.currentTimeMillis() + SHOW_MILLIS;
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        try {
            render(event);
        } catch (Throwable t) {
            // An exception mid-stage can make the level renderer skip later stages entirely.
            long now = System.currentTimeMillis();
            if (now - lastErrorLogMs > 1000L) {
                lastErrorLogMs = now;
                Townstead.LOGGER.warn("[EnclosureGap] render error swallowed: {}", t.toString());
            }
        }
    }

    private static void render(RenderLevelStageEvent event) {
        //? if neoforge {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        //?} else if forge {
        /*if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        *///?}
        BlockPos pos = gap;
        if (pos == null) return;
        long now = System.currentTimeMillis();
        if (now > until) {
            gap = null;
            return;
        }
        // A slow pulse, so the outline reads as a marker rather than part of the block.
        float alpha = 0.55F + 0.45F * (float) Math.sin(now / 160.0);
        Vec3 cam = event.getCamera().getPosition();
        AABB box = new AABB(pos).inflate(0.02);
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-cam.x, -cam.y, -cam.z);
        //? if neoforge {
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        LevelRenderer.renderLineBox(poseStack, buffers.getBuffer(RenderType.lines()), box, RED, GREEN, BLUE, alpha);
        buffers.endBatch(RenderType.lines());
        //?} else if forge {
        /*renderImmediate(poseStack.last().pose(), box, alpha);
        *///?}
        poseStack.popPose();
    }

    //? if forge {
    /*private static void renderImmediate(Matrix4f matrix, AABB box, float alpha) {
        RenderSystem.enableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.lineWidth(2.0F);
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        buffer.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        float x0 = (float) box.minX, y0 = (float) box.minY, z0 = (float) box.minZ;
        float x1 = (float) box.maxX, y1 = (float) box.maxY, z1 = (float) box.maxZ;
        float[][] edges = {
                {x0, y0, z0, x1, y0, z0}, {x0, y0, z1, x1, y0, z1}, {x0, y1, z0, x1, y1, z0}, {x0, y1, z1, x1, y1, z1},
                {x0, y0, z0, x0, y1, z0}, {x1, y0, z0, x1, y1, z0}, {x0, y0, z1, x0, y1, z1}, {x1, y0, z1, x1, y1, z1},
                {x0, y0, z0, x0, y0, z1}, {x1, y0, z0, x1, y0, z1}, {x0, y1, z0, x0, y1, z1}, {x1, y1, z0, x1, y1, z1}};
        for (float[] e : edges) {
            buffer.vertex(matrix, e[0], e[1], e[2]).color(RED, GREEN, BLUE, alpha).endVertex();
            buffer.vertex(matrix, e[3], e[4], e[5]).color(RED, GREEN, BLUE, alpha).endVertex();
        }
        tesselator.end();
        RenderSystem.lineWidth(1.0F);
        RenderSystem.disableBlend();
    }
    *///?}
}
