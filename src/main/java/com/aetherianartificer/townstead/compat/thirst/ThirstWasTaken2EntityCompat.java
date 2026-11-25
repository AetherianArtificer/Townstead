package com.aetherianartificer.townstead.compat.thirst;

import com.aetherianartificer.townstead.compat.ModCompat;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/** ThirstWasTaken2 effects whose drain the mod applies from its player tick only. */
public final class ThirstWasTaken2EntityCompat {
    /** Parched: four points of thirst a minute per level, at 4 exhaustion a point. */
    private static final float PARCHED_PER_LEVEL = 16f / 1200f;

    private ThirstWasTaken2EntityCompat() {}

    public static float thirstExhaustion(LivingEntity entity) {
        if (!ModCompat.isLoaded(ThirstWasTaken2Bridge.MOD_ID)) return 0f;
        MobEffectInstance parched = ThirstEffects.active(entity, ThirstWasTaken2Bridge.MOD_ID, "parched");
        return parched == null ? 0f : ThirstEffects.townsteadExhaustion(PARCHED_PER_LEVEL * (parched.getAmplifier() + 1));
    }
}
