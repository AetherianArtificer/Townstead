package com.aetherianartificer.townstead.client.origin;

import com.aetherianartificer.townstead.origin.GeneCatalogEntry;
import com.aetherianartificer.townstead.origin.OriginCatalogEntry;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Renders the REAL particles of a previewed origin's {@code particle} genes over the
 * editor model. Uses the genuine particle system: {@code createParticle} builds the
 * actual {@code Particle} (its real motion/sprites), which is then ticked and drawn with
 * the real {@link Particle#render}. The particle engine's own renderer targets the world
 * (occluded by the editor background), so this hosts the particles in a small 3D pass
 * framed by a {@link Camera} to sit over the model. Particles live at a high, sky-lit air
 * point above the player so they're in a loaded, full-bright, collision-free spot.
 *
 * <p>The framing constants ({@link #FOV}, {@link #DIST}) are tuned visually; the
 * model render box comes from the caller (MCA's entity render rect).</p>
 */
public final class PreviewParticles {

    private static final int MAX = 80;
    private static final float EMIT_INTERVAL = 8f;   // ticks between emissions
    private static final float DIST = 2.0f;          // blocks in front of the camera
    private static final float MODEL_SCALE = 60f;    // MCA's renderEntityInInventory scale (~px per block)

    private PreviewParticles() {}

    private static final List<Particle> LIVE = new ArrayList<>();
    private static final RandomSource RAND = RandomSource.create();
    private static String lastOrigin = "";
    private static long lastTimeMs;
    private static float tickAcc;
    private static float spawnAcc;
    private static GuiCamera camera;

    public static void clear() {
        LIVE.clear();
        lastOrigin = "";
        spawnAcc = 0f;
        tickAcc = 0f;
    }

    public static void render(GuiGraphics ctx, Entity entity, int x0, int y0, int x1, int y1) {
        if (entity == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        String origin = OriginClientStore.get(entity.getId());
        if (!origin.equals(lastOrigin)) {
            LIVE.clear();
            spawnAcc = 0f;
            lastOrigin = origin;
        }
        List<GeneCatalogEntry> specs = particleGenes(origin);

        long now = System.currentTimeMillis();
        float dt = lastTimeMs == 0 ? 1f : Math.min(4f, (now - lastTimeMs) / 50f);
        lastTimeMs = now;

        // Tick the real particles on the 20 Hz cadence they expect.
        tickAcc += dt;
        while (tickAcc >= 1f) {
            tickAcc -= 1f;
            for (Particle p : LIVE) p.tick();
        }
        LIVE.removeIf(p -> !p.isAlive());

        if (!specs.isEmpty()) {
            spawnAcc += dt;
            if (spawnAcc >= EMIT_INTERVAL) {
                spawnAcc = 0f;
                for (GeneCatalogEntry g : specs) spawn(mc, g);
            }
        }

        if (LIVE.isEmpty()) return;
        drawPass(mc, x0, y0, x1, y1);
    }

    private static void drawPass(Minecraft mc, int x0, int y0, int x1, int y1) {
        Camera cam = camera();
        double gs = mc.getWindow().getGuiScale();
        int fbH = mc.getWindow().getHeight();
        int vx = (int) (x0 * gs);
        int vy = (int) (fbH - y1 * gs);     // GL viewport origin is bottom-left
        int vw = Math.max(1, (int) ((x1 - x0) * gs));
        int vh = Math.max(1, (int) ((y1 - y0) * gs));

        // Frame the view so on-screen pixels-per-block match MCA's model scale: the box
        // height (in GUI px) over MODEL_SCALE gives the blocks the view must span vertically.
        float visibleBlocks = (y1 - y0) / MODEL_SCALE;
        float fov = (float) (2.0 * Math.atan((visibleBlocks / 2f) / DIST));
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(
                new Matrix4f().perspective(fov, (float) vw / vh, 0.05f, 64f),
                VertexSorting.DISTANCE_TO_ORIGIN);
        pushIdentityModelView();
        RenderSystem.viewport(vx, vy, vw, vh);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // The model is already in the depth buffer in a different projection; testing the
        // overlay against it would let the model occlude the particles. Drawn last, so just
        // paint on top.
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        mc.gameRenderer.lightTexture().turnOnLightLayer();

        TextureManager tm = mc.getTextureManager();
        for (Particle p : LIVE) flushParticle(p, cam, tm);

        mc.gameRenderer.lightTexture().turnOffLightLayer();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.viewport(0, 0, mc.getWindow().getWidth(), fbH);
        popModelView();
        RenderSystem.restoreProjectionMatrix();
    }

    private static void spawn(Minecraft mc, GeneCatalogEntry g) {
        ParticleOptions options = options(g.particleId());
        if (options == null) return;
        Vec3 a = anchor(mc);
        float spread = g.particleSpread();
        float speed = g.particleSpeed();
        int count = Math.max(1, g.particleCount());
        for (int i = 0; i < count && LIVE.size() < MAX; i++) {
            double px = a.x + (RAND.nextFloat() * 2 - 1) * spread;
            double py = a.y + (g.particleYOffset() * 2f - 1f) + (RAND.nextFloat() * 2 - 1) * spread * 0.5f;
            double pz = a.z - DIST;
            Particle p = mc.particleEngine.createParticle(options, px, py, pz, 0, speed, 0);
            if (p != null) LIVE.add(p);
        }
    }

    /** A high, sky-lit, collision-free air point in the player's (loaded) column. */
    private static Vec3 anchor(Minecraft mc) {
        double x = mc.player != null ? mc.player.getX() : 0;
        double z = mc.player != null ? mc.player.getZ() : 0;
        double y = mc.level.getMaxBuildHeight() - 4;
        return new Vec3(x, y, z);
    }

    private static Camera camera() {
        if (camera == null) camera = new GuiCamera();
        camera.place(anchor(Minecraft.getInstance()));
        return camera;
    }

    private static List<GeneCatalogEntry> particleGenes(String originId) {
        OriginCatalogEntry origin = originId == null || originId.isEmpty()
                ? null : OriginCatalogClient.origin(originId);
        if (origin == null) return List.of();
        List<GeneCatalogEntry> out = new ArrayList<>();
        for (OriginCatalogEntry.Inherited inherited : origin.inheritedGenes()) {
            GeneCatalogEntry gene = OriginCatalogClient.gene(inherited.geneId());
            if (gene != null && gene.isParticle()) out.add(gene);
        }
        return out;
    }

    private static ParticleOptions options(String particleId) {
        ResourceLocation id = ResourceLocation.tryParse(particleId);
        if (id == null) return null;
        ParticleType<?> type = BuiltInRegistries.PARTICLE_TYPE.get(id);
        return type instanceof ParticleOptions o ? o : null;   // simple particles only
    }

    // A camera fixed at the anchor facing -Z, so particles placed in front (-Z) are framed
    // and billboard toward the viewer. Subclass to reach Camera's protected setters.
    private static final class GuiCamera extends Camera {
        void place(Vec3 pos) {
            setPosition(pos);
            setRotation(180f, 0f);   // yaw 180 -> look down -Z
        }
    }

    private static void pushIdentityModelView() {
        //? if >=1.21 {
        org.joml.Matrix4fStack s = RenderSystem.getModelViewStack();
        s.pushMatrix();
        s.identity();
        //?} else {
        /*com.mojang.blaze3d.vertex.PoseStack s = RenderSystem.getModelViewStack();
        s.pushPose();
        s.setIdentity();
        *///?}
        RenderSystem.applyModelViewMatrix();
    }

    private static void popModelView() {
        //? if >=1.21 {
        RenderSystem.getModelViewStack().popMatrix();
        //?} else {
        /*RenderSystem.getModelViewStack().popPose();
        *///?}
        RenderSystem.applyModelViewMatrix();
    }

    private static void flushParticle(Particle p, Camera cam, TextureManager tm) {
        //? if >=1.21 {
        com.mojang.blaze3d.vertex.Tesselator tess = com.mojang.blaze3d.vertex.Tesselator.getInstance();
        com.mojang.blaze3d.vertex.BufferBuilder buffer = p.getRenderType().begin(tess, tm);
        p.render(buffer, cam, 1.0f);
        com.mojang.blaze3d.vertex.MeshData mesh = buffer.build();
        if (mesh != null) com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(mesh);
        //?} else {
        /*com.mojang.blaze3d.vertex.Tesselator tess = com.mojang.blaze3d.vertex.Tesselator.getInstance();
        com.mojang.blaze3d.vertex.BufferBuilder buffer = tess.getBuilder();
        net.minecraft.client.particle.ParticleRenderType rt = p.getRenderType();
        rt.begin(buffer, tm);
        p.render(buffer, cam, 1.0f);
        rt.end(tess);
        *///?}
    }
}
