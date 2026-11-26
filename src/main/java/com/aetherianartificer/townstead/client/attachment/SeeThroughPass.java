package com.aetherianartificer.townstead.client.attachment;

import com.aetherianartificer.townstead.Townstead;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.Util;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
//? if neoforge {
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
//?} else if forge {
/*import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
*///?}

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws see-through attachments the way see-through things have to be drawn: after everything solid, furthest
 * first, and without getting in the way of anything else that is see-through.
 *
 * <p>While bodies draw, each see-through attachment's faces are kept here instead of drawn. Once the solid world,
 * the entities and the block entities are down, the parts are composed on a screen of their own, against a copy of
 * the depth drawn so far: each part as a far and a near wall, furthest part first. The shared depth is never
 * written, so nothing drawn later in the frame (water, a mod's see-through block, particles) can be hidden behind
 * a part.</p>
 *
 * <p>The finished parts then join the frame. With Fabulous graphics they go in as one more of the see-through
 * layers vanilla sorts per pixel at the end of the frame (the one dropped items use), so water, glass, particles,
 * weather and clouds fall in front of or behind them as they are. Otherwise they are laid over the frame once the
 * weather is down. With a shader pack on, the pack orders see-through things itself, and the faces go straight
 * to it.</p>
 *
 * <p>Draws in a menu (an inventory preview) also go straight out: there's no world pass to wait for there.</p>
 */
//? if neoforge {
@EventBusSubscriber(modid = Townstead.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
//?} else {
/*@Mod.EventBusSubscriber(modid = Townstead.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
*///?}
public final class SeeThroughPass {

    // A quad: its render type, then four vertices of position, colour, texture, overlay, light and normal. Quads
    // of one part as drawn on one body share a group.
    private record Quad(RenderType type, RenderType depthOnly, float[] data, float depth, int group) {}

    private static final int FLOATS = 12;
    private static final int MAX_QUADS = 60_000;
    // Faces kept longer than this were never drawn (no world pass ran): let them go.
    private static final long STALE_MS = 500L;

    private static final List<Quad> QUADS = new ArrayList<>();
    private static long keptAt;
    private static int groups;

    // The parts' own screen, and whether it holds this frame's parts yet to join the frame.
    private static RenderTarget screen;
    // The frame's depth as the parts join it, see-through blocks included (without Fabulous graphics).
    private static RenderTarget scene;
    // How much of a part shows under water or glass in front of it, without Fabulous graphics.
    private static final float UNDER = 0.45f;
    private static boolean composed;
    private static ShaderInstance composite;
    //? if neoforge {
    private static final MultiBufferSource.BufferSource OWN = MultiBufferSource.immediate(new ByteBufferBuilder(1 << 18));
    //?} else {
    /*private static final MultiBufferSource.BufferSource OWN = MultiBufferSource.immediate(new BufferBuilder(1 << 18));
    *///?}

    private SeeThroughPass() {}

    @SubscribeEvent
    public static void registerShaders(RegisterShadersEvent event) {
        try {
            //? if neoforge {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "see_through_composite");
            //?} else {
            /*ResourceLocation id = new ResourceLocation(Townstead.MOD_ID, "see_through_composite");
            *///?}
            event.registerShader(new ShaderInstance(event.getResourceProvider(), id, DefaultVertexFormat.POSITION),
                    shader -> composite = shader);
        } catch (Exception e) {
            // Without it, see-through parts draw straight out.
            Townstead.LOGGER.warn("[SeeThrough] composite shader failed to load: {}", e.toString());
            composite = null;
        }
    }

    /** Where a see-through attachment's faces go: kept for the pass in the world, straight out otherwise. */
    public static VertexConsumer consumer(RenderType type, ResourceLocation texture,
                                          MultiBufferSource buffers, boolean inWorld) {
        if (!inWorld || composite == null || ShaderPacks.inUse()) return buffers.getBuffer(type);
        long now = Util.getMillis();
        if (!QUADS.isEmpty() && now - keptAt > STALE_MS) QUADS.clear();
        keptAt = now;
        return new Keeper(type, AttachmentRenderTypes.seeThroughDepth(texture), groups++);
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) compose(event);
        else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_WEATHER) join();
    }

    /**
     * Every kept part onto the parts' own screen, against the depth of what's solid so far.
     *
     * <p>A part is drawn the way a see-through solid has to be: its far wall, then its near wall, each by depth
     * first and colour only where it is that wall's nearest surface. So whatever sits behind it is seen through
     * exactly two layers, however many faces the author stacked inside (plates, segments, inner walls), and its
     * {@code opacity} means the same for every part. Drawing every face blended instead multiplies the tint once
     * per layer and buries what's behind.</p>
     */
    private static void compose(RenderLevelStageEvent event) {
        composed = false;
        groups = 0;
        if (QUADS.isEmpty()) return;
        Map<Integer, List<Quad>> byGroup = new HashMap<>();
        Map<Integer, Float> nearest = new HashMap<>();
        for (Quad quad : QUADS) {
            byGroup.computeIfAbsent(quad.group(), g -> new ArrayList<>()).add(quad);
            nearest.merge(quad.group(), quad.depth(), Math::min);
        }
        QUADS.clear();
        List<Integer> order = new ArrayList<>(byGroup.keySet());
        order.sort((a, b) -> Float.compare(nearest.get(b), nearest.get(a)));

        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        // The last body drawn can still be waiting in the buffers: down with it, so its depth is copied too.
        Minecraft.getInstance().renderBuffers().bufferSource().endLastBatch();
        int bound = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        try {
            RenderTarget own = screen(main);
            own.clear(Minecraft.ON_OSX);
            own.copyDepthFrom(main);
            own.bindWrite(false);
            //? if neoforge {
            PoseStack.Pose pose = event.getPoseStack().last();
            //?} else {
            /*// Kept faces are already in view space here.
            PoseStack.Pose pose = new PoseStack().last();
            *///?}
            for (int group : order) {
                List<Quad> far = new ArrayList<>(), near = new ArrayList<>();
                for (Quad quad : byGroup.get(group)) (facesCamera(quad) ? near : far).add(quad);
                draw(pose, far, true);
                draw(pose, far, false);
                draw(pose, near, true);
                draw(pose, near, false);
            }
            composed = true;
        } finally {
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, bound);
        }
    }

    /**
     * The composed parts into the frame. With Fabulous graphics, into the dropped items' layer, with the depth of
     * each part's nearest face, for vanilla to sort against the other see-through layers; otherwise over the frame.
     */
    private static void join() {
        if (!composed || screen == null || composite == null) return;
        composed = false;
        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget layer = Minecraft.useShaderTransparency() ? minecraft.levelRenderer.getItemEntityTarget() : null;
        RenderTarget into = layer != null ? layer : minecraft.getMainRenderTarget();
        int bound = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        boolean depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        if (layer == null) {
            // Without vanilla's sorting, a part with water or glass in front of it is laid over them, faded so they
            // still read as in front.
            scene = match(scene, into);
            scene.copyDepthFrom(into);
        }
        into.bindWrite(false);
        RenderSystem.enableBlend();
        // The parts' screen holds colour already weighed by its alpha.
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        if (layer != null) {
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(true);
        } else {
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
        }
        composite.setSampler("PartsSampler", screen.getColorTextureId());
        composite.setSampler("PartsDepthSampler", screen.getDepthTextureId());
        composite.setSampler("SceneDepthSampler", layer == null ? scene.getDepthTextureId() : screen.getDepthTextureId());
        composite.safeGetUniform("Under").set(layer == null ? UNDER : 1f);
        RenderSystem.setShader(() -> composite);
        //? if neoforge {
        BufferBuilder quad = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        quad.addVertex(0f, 0f, 0f);
        quad.addVertex(1f, 0f, 0f);
        quad.addVertex(1f, 1f, 0f);
        quad.addVertex(0f, 1f, 0f);
        BufferUploader.drawWithShader(quad.buildOrThrow());
        //?} else {
        /*BufferBuilder quad = Tesselator.getInstance().getBuilder();
        quad.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        quad.vertex(0, 0, 0).endVertex();
        quad.vertex(1, 0, 0).endVertex();
        quad.vertex(1, 1, 0).endVertex();
        quad.vertex(0, 1, 0).endVertex();
        BufferUploader.drawWithShader(quad.end());
        *///?}
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(depthWrite);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, bound);
    }

    // The parts' screen.
    private static RenderTarget screen(RenderTarget main) {
        screen = match(screen, main);
        return screen;
    }

    // A screen the size of the frame, holding depth the same way.
    private static RenderTarget match(RenderTarget target, RenderTarget main) {
        if (target == null) {
            target = new TextureTarget(main.width, main.height, true, Minecraft.ON_OSX);
            target.setClearColor(0f, 0f, 0f, 0f);
        } else if (target.width != main.width || target.height != main.height) {
            target.resize(main.width, main.height, Minecraft.ON_OSX);
        }
        // Depth copies across only between matching formats.
        if (main.isStencilEnabled() && !target.isStencilEnabled()) target.enableStencil();
        return target;
    }

    // Whether a face's front looks toward the camera (faces are kept relative to it).
    private static boolean facesCamera(Quad quad) {
        float[] d = quad.data();
        float cx = 0f, cy = 0f, cz = 0f;
        for (int v = 0; v < 4; v++) {
            cx += d[v * FLOATS];
            cy += d[v * FLOATS + 1];
            cz += d[v * FLOATS + 2];
        }
        float n = d[11];
        return unpackX(n) * cx + unpackY(n) * cy + unpackZ(n) * cz < 0f;
    }

    private static void draw(PoseStack.Pose pose, List<Quad> quads, boolean depth) {
        RenderType current = null;
        VertexConsumer out = null;
        for (Quad quad : quads) {
            RenderType type = depth ? quad.depthOnly() : quad.type();
            if (type != current) {
                if (current != null) OWN.endBatch(current);
                current = type;
                out = OWN.getBuffer(current);
            }
            emit(out, pose, quad.data());
        }
        if (current != null) OWN.endBatch(current);
    }

    private static void emit(VertexConsumer out, PoseStack.Pose pose, float[] d) {
        for (int v = 0; v < 4; v++) {
            int b = v * FLOATS;
            //? if neoforge {
            out.addVertex(pose, d[b], d[b + 1], d[b + 2])
                    .setColor((int) d[b + 3], (int) d[b + 4], (int) d[b + 5], (int) d[b + 6])
                    .setUv(d[b + 7], d[b + 8])
                    .setOverlay((int) d[b + 9])
                    .setLight((int) d[b + 10])
                    .setNormal(pose, unpackX(d[b + 11]), unpackY(d[b + 11]), unpackZ(d[b + 11]));
            //?} else {
            /*out.vertex(pose.pose(), d[b], d[b + 1], d[b + 2])
                    .color((int) d[b + 3], (int) d[b + 4], (int) d[b + 5], (int) d[b + 6])
                    .uv(d[b + 7], d[b + 8])
                    .overlayCoords((int) d[b + 9])
                    .uv2((int) d[b + 10])
                    .normal(pose.normal(), unpackX(d[b + 11]), unpackY(d[b + 11]), unpackZ(d[b + 11]))
                    .endVertex();
            *///?}
        }
    }

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

    // Whether a shader pack (Iris, Oculus) is drawing the world.
    private static final class ShaderPacks {
        private static Object api;
        private static Method inUse;
        private static boolean looked;

        static boolean inUse() {
            if (!looked) {
                looked = true;
                try {
                    Class<?> type = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                    api = type.getMethod("getInstance").invoke(null);
                    inUse = type.getMethod("isShaderPackInUse");
                } catch (Throwable ignored) {
                    api = null;
                }
            }
            if (api == null) return false;
            try {
                return (Boolean) inUse.invoke(api);
            } catch (Throwable t) {
                return false;
            }
        }
    }

    /** Keeps faces as they're drawn, already placed by the body's pose, four vertices to a face. */
    private static final class Keeper implements VertexConsumer {
        private final RenderType type, depthOnly;
        private final int group;
        private float[] quad = new float[FLOATS * 4];
        private int vertex = -1;

        Keeper(RenderType type, RenderType depthOnly, int group) {
            this.type = type;
            this.depthOnly = depthOnly;
            this.group = group;
        }

        private int at() {
            return vertex * FLOATS;
        }

        private void begin(float x, float y, float z) {
            vertex++;
            int b = at();
            quad[b] = x;
            quad[b + 1] = y;
            quad[b + 2] = z;
            quad[b + 3] = quad[b + 4] = quad[b + 5] = quad[b + 6] = 255f;
        }

        private void color(int o, int r, int g, int b, int a) {
            quad[o + 3] = r;
            quad[o + 4] = g;
            quad[o + 5] = b;
            quad[o + 6] = a;
        }

        // The last vertex of a face is in: keep the face.
        private void finish() {
            if (vertex != 3) return;
            float cx = 0f, cy = 0f, cz = 0f;
            for (int v = 0; v < 4; v++) {
                cx += quad[v * FLOATS];
                cy += quad[v * FLOATS + 1];
                cz += quad[v * FLOATS + 2];
            }
            cx /= 4f;
            cy /= 4f;
            cz /= 4f;
            if (QUADS.size() < MAX_QUADS) QUADS.add(new Quad(type, depthOnly, quad, cx * cx + cy * cy + cz * cz, group));
            quad = new float[FLOATS * 4];
            vertex = -1;
        }

        //? if neoforge {
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            begin(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            color(at(), r, g, b, a);
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
            // The normal is the last of a vertex.
            quad[at() + 11] = pack(x, y, z);
            finish();
            return this;
        }
        //?} else {
        /*@Override
        public VertexConsumer vertex(double x, double y, double z) {
            begin((float) x, (float) y, (float) z);
            return this;
        }

        @Override
        public VertexConsumer color(int r, int g, int b, int a) {
            color(at(), r, g, b, a);
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            quad[at() + 7] = u;
            quad[at() + 8] = v;
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            quad[at() + 9] = (v << 16) | (u & 0xFFFF);
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            quad[at() + 10] = (v << 16) | (u & 0xFFFF);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            quad[at() + 11] = pack(x, y, z);
            return this;
        }

        @Override
        public void endVertex() {
            finish();
        }

        @Override
        public void defaultColor(int r, int g, int b, int a) {}

        @Override
        public void unsetDefaultColor() {}
        *///?}
    }
}
