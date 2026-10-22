package com.aetherianartificer.townstead.client.particle;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.client.attachment.AttachmentClient;
import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.particle.DataParticleDef;
import com.aetherianartificer.townstead.pheno.particle.DataParticleOptions;
import com.aetherianartificer.townstead.pheno.particle.DataParticleTypes;
import com.aetherianartificer.townstead.pheno.particle.DataParticles;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
//? if neoforge {
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
//?} else {
/*import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
*///?}

/**
 * One data-pack particle. Its sprite is a synced data-pack texture rather than an atlas sprite, so
 * it draws itself through an entity render type bound to that texture (the {@code CUSTOM} particle
 * path vanilla uses for item pickups) instead of the particle sheet.
 */
//? if neoforge {
@EventBusSubscriber(modid = Townstead.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
//?} else {
/*@Mod.EventBusSubscriber(modid = Townstead.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
*///?}
public final class DataParticle extends Particle {

    private final DataParticleDef def;
    private final float baseSize;
    private final float peakAlpha;
    private final float spinPerTick;
    private float size;

    private DataParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd,
                         DataParticleDef def) {
        super(level, x, y, z);
        this.def = def;
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.lifetime = def.lifetimeMin() + (def.lifetimeMax() > def.lifetimeMin()
                ? random.nextInt(def.lifetimeMax() - def.lifetimeMin() + 1) : 0);
        this.baseSize = Mth.lerp(random.nextFloat(), def.sizeMin(), def.sizeMax());
        this.size = baseSize;
        int rgb = def.colors()[random.nextInt(def.colors().length)];
        setColor(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f);
        this.peakAlpha = def.alpha();
        this.alpha = def.fadeIn() > 0 ? 0f : peakAlpha;
        this.gravity = def.gravity();
        this.friction = def.drag();
        this.hasPhysics = def.collide();
        this.spinPerTick = def.spin() == 0 ? 0 : (float) Math.toRadians(def.spin()) * (random.nextBoolean() ? 1 : -1);
        this.roll = random.nextFloat() * (float) (Math.PI * 2);
        this.oRoll = roll;
    }

    @Override
    public void tick() {
        super.tick();
        if (!isAlive()) return;
        float life = age / (float) lifetime;
        oRoll = roll;
        roll = def.tumble() != 0 ? Mth.sin(age * 0.18f) * def.tumble() : roll + spinPerTick;
        if (def.sway() != 0) {
            xd += Math.sin((age + 7) * def.swayRate()) * def.sway();
            zd += Math.cos((age + 3) * def.swayRate() * 0.86) * def.sway();
        }
        float in = def.fadeIn() > 0 ? Math.min(1f, age / (float) def.fadeIn()) : 1f;
        float fadeOutTicks = def.fadeOutFraction() > 0 ? def.fadeOutFraction() * lifetime : def.fadeOut();
        float out = fadeOutTicks > 0 ? Math.min(1f, (lifetime - age) / fadeOutTicks) : 1f;
        float a = peakAlpha * Math.min(in, out);
        if (def.flicker() > 0) a *= 1f - def.flicker() * random.nextFloat();
        alpha = Math.max(0f, a);
        float s = def.shrink() ? baseSize * Math.max(0f, 1f - life) : baseSize;
        if (def.pulse() > 0) s *= 1f + def.pulse() * Mth.sin(age * def.pulseRate());
        if (def.flare() != 1f && life > 0.8f) s *= def.flare();
        size = Math.max(0f, s);
    }

    private int frame() {
        if (def.frames() <= 1) return 0;
        int f = def.frameTime() > 0 ? age / def.frameTime() : age * def.frames() / Math.max(1, lifetime);
        return Math.min(def.frames() - 1, Math.max(0, f)) % def.frames();
    }

    @Override
    public void render(VertexConsumer ignored, Camera camera, float partialTicks) {
        if (alpha <= 0.003f) return;
        // Resolved per frame: a particle spawned before its synced texture arrives picks it up.
        ResourceLocation synced = AttachmentClient.namedTexture(def.texture());
        ResourceLocation texture = synced != null ? synced : DataPackLang.parseId(def.texture());
        if (texture == null) return;
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        RenderType type = def.glow() ? RenderType.entityTranslucentEmissive(texture) : RenderType.entityTranslucent(texture);
        VertexConsumer vc = buffers.getBuffer(type);
        Vec3 cam = camera.getPosition();
        float px = (float) (Mth.lerp(partialTicks, xo, x) - cam.x());
        float py = (float) (Mth.lerp(partialTicks, yo, y) - cam.y());
        float pz = (float) (Mth.lerp(partialTicks, zo, z) - cam.z());
        Quaternionf rotation = new Quaternionf(camera.rotation());
        float r = Mth.lerp(partialTicks, oRoll, roll);
        if (r != 0) rotation.rotateZ(r);
        float v0 = frame() / (float) def.frames();
        float v1 = (frame() + 1) / (float) def.frames();
        int light = def.glow() ? 0xF000F0 : getLightColor(partialTicks);
        vertex(vc, rotation, px, py, pz, -1, -1, 1f, v1, light);
        vertex(vc, rotation, px, py, pz, -1, 1, 1f, v0, light);
        vertex(vc, rotation, px, py, pz, 1, 1, 0f, v0, light);
        vertex(vc, rotation, px, py, pz, 1, -1, 0f, v1, light);
        buffers.endBatch(type);
    }

    private void vertex(VertexConsumer vc, Quaternionf rotation, float px, float py, float pz,
                        float cx, float cy, float u, float v, int light) {
        Vector3f corner = new Vector3f(cx, cy, 0).rotate(rotation).mul(size).add(px, py, pz);
        //? if >=1.21 {
        vc.addVertex(corner.x(), corner.y(), corner.z()).setColor(rCol, gCol, bCol, alpha).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0f, 1f, 0f);
        //?} else {
        /*vc.vertex(corner.x(), corner.y(), corner.z()).color(rCol, gCol, bCol, alpha).uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(0f, 1f, 0f).endVertex();
        *///?}
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.CUSTOM;
    }

    /** Builds a particle from its synced definition; an unknown id draws nothing. */
    private static final ParticleProvider<DataParticleOptions> PROVIDER =
            (options, level, x, y, z, xd, yd, zd) -> {
                DataParticleDef def = DataParticles.get(options.id());
                return def == null ? null : new DataParticle(level, x, y, z, xd, yd, zd, def);
            };

    @SubscribeEvent
    public static void register(RegisterParticleProvidersEvent event) {
        event.registerSpecial(DataParticleTypes.DATA.get(), PROVIDER);
    }
}
