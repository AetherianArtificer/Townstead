package com.aetherianartificer.townstead.compat.thirst;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/** Finds an active effect by its registry id, for thirst mods whose effects tick on players only. */
final class ThirstEffects {
    private ThirstEffects() {}

    static MobEffectInstance active(LivingEntity entity, String namespace, String path) {
        for (MobEffectInstance effect : entity.getActiveEffects()) {
            //? if >=1.21 {
            ResourceLocation key = BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value());
            //?} else {
            /*ResourceLocation key = BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect());
            *///?}
            if (key != null && key.getNamespace().equals(namespace) && key.getPath().equals(path)) return effect;
        }
        return null;
    }

    /** A mod's per-tick exhaustion, spent at 4 a point, on Townstead's threshold. */
    static float townsteadExhaustion(float modExhaustion) {
        return modExhaustion * (com.aetherianartificer.townstead.thirst.ThirstData.EXHAUSTION_THRESHOLD / 4f);
    }
}
