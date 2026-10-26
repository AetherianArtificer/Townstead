package com.aetherianartificer.townstead.client.haze;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.block.haze.HazeParticles;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
//? if neoforge {
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
//?} else {
/*import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
*///?}

/** One soft, drifting puff of a cloud haze, tinted to its kind and as strong as its cell is dense. */
//? if neoforge {
@EventBusSubscriber(modid = Townstead.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
//?} else {
/*@Mod.EventBusSubscriber(modid = Townstead.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
*///?}
public final class HazePuffParticle extends TextureSheetParticle {
    private static final int FADE_IN = 12;
    private static final int FADE_OUT = 24;
    private final float peakAlpha;

    private HazePuffParticle(ClientLevel level, double x, double y, double z, int rgb, float strength,
                             boolean conceals, SpriteSet sprites) {
        super(level, x, y, z);
        pickSprite(sprites);
        setColor(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f);
        peakAlpha = conceals ? 0.35f + 0.6f * strength : 0.12f + 0.33f * strength;
        alpha = 0f;
        lifetime = 70 + random.nextInt(40);
        quadSize = (conceals ? 1.2f : 1.0f) + random.nextFloat() * 0.5f;
        hasPhysics = false;
        gravity = 0;
        friction = 1;
        xd = (random.nextDouble() - .5) * .01;
        yd = .002 + random.nextDouble() * .004;
        zd = (random.nextDouble() - .5) * .01;
    }

    @Override
    public void tick() {
        super.tick();
        float in = Math.min(1f, age / (float) FADE_IN);
        float out = Math.min(1f, (lifetime - age) / (float) FADE_OUT);
        alpha = peakAlpha * Math.min(in, out);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @SubscribeEvent
    public static void register(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(HazeParticles.PUFF.get(), sprites ->
                (type, level, x, y, z, rgb, strength, conceals) ->
                        new HazePuffParticle(level, x, y, z, (int) rgb, (float) strength, conceals > 0.5, sprites));
    }
}
