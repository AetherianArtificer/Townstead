package com.aetherianartificer.townstead.client.attachment;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.Util;
//? if neoforge {
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
//?} else if forge {
/*import net.minecraftforge.client.event.RenderLevelStageEvent;
*///?}

import java.util.ArrayList;
import java.util.List;

/**
 * Draws see-through attachments the way see-through things have to be drawn: after everything solid in the
 * frame, furthest first. While bodies draw, each see-through attachment's faces are kept here instead of drawn;
 * once every entity is down, they're drawn in one pass, sorted back to front across every body.
 *
 * <p>So an ear behind a clear sac shows through it, a villager's sac in front of another's tail tints it, and
 * nobody authoring a root has to do anything but make the part see-through. Draws in a menu (an inventory
 * preview) go straight out: there's no world pass to wait for there.</p>
 */
public final class SeeThroughPass {

    // A quad: its render type, then four vertices of position, colour, texture, overlay, light and normal.
    private record Quad(RenderType type, RenderType depthOnly, float[] data, float depth) {}

    private static final int FLOATS = 12;
    private static final int MAX_QUADS = 60_000;
    // Faces kept longer than this were never drawn (no world pass ran): let them go.
    private static final long STALE_MS = 500L;

    private static final List<Quad> QUADS = new ArrayList<>();
    private static long keptAt;

    private SeeThroughPass() {}

    /** Where a see-through attachment's faces go: kept for the pass in the world, straight out in a menu. */
    public static VertexConsumer consumer(RenderType type, net.minecraft.resources.ResourceLocation texture,
                                          MultiBufferSource buffers, boolean inWorld) {
        //? if neoforge {
        if (!inWorld) return buffers.getBuffer(type);
        long now = Util.getMillis();
        if (!QUADS.isEmpty() && now - keptAt > STALE_MS) QUADS.clear();
        keptAt = now;
        return new Keeper(type, AttachmentRenderTypes.seeThroughDepth(texture));
        //?} else {
        /*return buffers.getBuffer(type);
        *///?}
    }

    /** After every entity: the kept faces, furthest first, over what's already drawn. */
    public static void render(RenderLevelStageEvent event) {
        //? if neoforge {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        if (QUADS.isEmpty()) return;
        List<Quad> quads = new ArrayList<>(QUADS);
        QUADS.clear();
        quads.sort((a, b) -> Float.compare(b.depth(), a.depth()));
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        PoseStack.Pose pose = event.getPoseStack().last();
        RenderType current = null;
        VertexConsumer out = null;
        for (Quad quad : quads) {
            if (quad.type() != current) {
                if (current != null) buffers.endBatch(current);
                current = quad.type();
                out = buffers.getBuffer(current);
            }
            emit(out, pose, quad.data());
        }
        if (current != null) buffers.endBatch(current);
        // Then their depth, so what's drawn later behind them stays behind them.
        current = null;
        for (Quad quad : quads) {
            if (quad.depthOnly() != current) {
                if (current != null) buffers.endBatch(current);
                current = quad.depthOnly();
                out = buffers.getBuffer(current);
            }
            emit(out, pose, quad.data());
        }
        if (current != null) buffers.endBatch(current);
        //?}
    }

    //? if neoforge {
    private static void emit(VertexConsumer out, PoseStack.Pose pose, float[] d) {
        for (int v = 0; v < 4; v++) {
            int b = v * FLOATS;
            out.addVertex(pose, d[b], d[b + 1], d[b + 2])
                    .setColor((int) d[b + 3], (int) d[b + 4], (int) d[b + 5], (int) d[b + 6])
                    .setUv(d[b + 7], d[b + 8])
                    .setOverlay((int) d[b + 9])
                    .setLight((int) d[b + 10])
                    .setNormal(pose, unpackX(d[b + 11]), unpackY(d[b + 11]), unpackZ(d[b + 11]));
        }
    }
    //?}

    // A normal packed into one float: three signed bytes.
    private static float pack(float x, float y, float z) {
        int bx = Math.round(Math.max(-1f, Math.min(1f, x)) * 127f) & 0xFF;
        int by = Math.round(Math.max(-1f, Math.min(1f, y)) * 127f) & 0xFF;
        int bz = Math.round(Math.max(-1f, Math.min(1f, z)) * 127f) & 0xFF;
        return Float.intBitsToFloat((bx << 16) | (by << 8) | bz);
    }

    private static float unpackX(float packed) {
        return ((byte) (Float.floatToRawIntBits(packed) >> 16)) / 127f;
    }

    private static float unpackY(float packed) {
        return ((byte) (Float.floatToRawIntBits(packed) >> 8)) / 127f;
    }

    private static float unpackZ(float packed) {
        return ((byte) Float.floatToRawIntBits(packed)) / 127f;
    }

    //? if neoforge {
    /** Keeps faces as they're drawn, already placed by the body's pose, four vertices to a face. */
    private static final class Keeper implements VertexConsumer {
        private final RenderType type, depthOnly;
        private float[] quad = new float[FLOATS * 4];
        private int vertex = -1;

        Keeper(RenderType type, RenderType depthOnly) {
            this.type = type;
            this.depthOnly = depthOnly;
        }

        private int at() {
            return vertex * FLOATS;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            vertex++;
            int b = at();
            quad[b] = x;
            quad[b + 1] = y;
            quad[b + 2] = z;
            quad[b + 3] = quad[b + 4] = quad[b + 5] = quad[b + 6] = 255f;
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            int o = at();
            quad[o + 3] = r;
            quad[o + 4] = g;
            quad[o + 5] = b;
            quad[o + 6] = a;
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            quad[at() + 7] = u;
            quad[at() + 8] = v;
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            quad[at() + 9] = (v << 16) | (u & 0xFFFF);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            quad[at() + 10] = (v << 16) | (u & 0xFFFF);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            quad[at() + 11] = pack(x, y, z);
            if (vertex == 3) {
                float cx = 0f, cy = 0f, cz = 0f;
                for (int v = 0; v < 4; v++) {
                    cx += quad[v * FLOATS];
                    cy += quad[v * FLOATS + 1];
                    cz += quad[v * FLOATS + 2];
                }
                cx /= 4f;
                cy /= 4f;
                cz /= 4f;
                if (QUADS.size() < MAX_QUADS) QUADS.add(new Quad(type, depthOnly, quad, cx * cx + cy * cy + cz * cz));
                quad = new float[FLOATS * 4];
                vertex = -1;
            }
            return this;
        }
    }
    //?}
}
