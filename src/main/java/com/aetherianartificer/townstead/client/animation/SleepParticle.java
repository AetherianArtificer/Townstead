package com.aetherianartificer.townstead.client.animation;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.fatigue.SleepParticles;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.particle.SpriteSet;
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

//? if neoforge {
@EventBusSubscriber(modid = Townstead.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
//?} else {
/*@Mod.EventBusSubscriber(modid = Townstead.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
*///?}
public final class SleepParticle extends TextureSheetParticle {
    private SleepParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z);
        pickSprite(sprites);
        lifetime = 48;
        quadSize = .14F;
        hasPhysics = false;
        gravity = 0;
        friction = 1;
        xd = (random.nextDouble() - .5) * .006;
        yd = .014;
        zd = (random.nextDouble() - .5) * .006;
    }
    @Override public void tick() {
        super.tick();
        alpha = Math.min(1F, (lifetime - age) / 16F);
        quadSize = .14F + .0015F * age;
    }
    @Override public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }

    @SubscribeEvent public static void register(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(SleepParticles.SLEEP.get(), sprites ->
                (type, level, x, y, z, dx, dy, dz) -> new SleepParticle(level, x, y, z, sprites));
    }
}
