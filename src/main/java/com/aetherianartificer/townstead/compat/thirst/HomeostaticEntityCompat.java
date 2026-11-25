package com.aetherianartificer.townstead.compat.thirst;

import com.aetherianartificer.townstead.compat.ModCompat;
import com.aetherianartificer.townstead.temperature.ThermalProtection;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/** Homeostatic effects that tick on players only, carried over to villagers. */
public final class HomeostaticEntityCompat {
    public static final String MOD_ID = "homeostatic";
    /** Its Thirst effect: 0.005 exhaustion a tick per level, at 4 exhaustion a point. */
    private static final float THIRST_PER_LEVEL = 0.005f;
    /** Frost Resistance stops the core from cooling at all. */
    private static final ThermalProtection FROST = new ThermalProtection(0, 10000, 0, 0);

    private HomeostaticEntityCompat() {}

    public static float thirstExhaustion(LivingEntity entity) {
        if (!ModCompat.isLoaded(MOD_ID)) return 0f;
        MobEffectInstance thirst = ThirstEffects.active(entity, MOD_ID, "thirst");
        return thirst == null ? 0f : ThirstEffects.townsteadExhaustion(THIRST_PER_LEVEL * (thirst.getAmplifier() + 1));
    }

    public static ThermalProtection protection(LivingEntity entity) {
        if (!ModCompat.isLoaded(MOD_ID)) return ThermalProtection.NONE;
        return ThirstEffects.active(entity, MOD_ID, "frost_resistance") != null ? FROST : ThermalProtection.NONE;
    }
}
