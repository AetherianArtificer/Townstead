package com.aetherianartificer.townstead.compat.temperature;

import com.aetherianartificer.townstead.compat.ModCompat;
import com.aetherianartificer.townstead.temperature.ThermalProtection;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/**
 * Cold Sweat effects that its own handlers only apply to players, read for MCA villagers. Grace
 * is full immunity; Ice Resistance and vanilla Fire Resistance block one side, each only while
 * Cold Sweat's own config lets it count.
 */
public final class ColdSweatEntityCompat {
    static final ThermalProtection ICE = new ThermalProtection(0, 10000, 0, 0);
    static final ThermalProtection FIRE = new ThermalProtection(0, 0, 10000, 0);

    private ColdSweatEntityCompat() {}

    public static boolean grace(LivingEntity entity) {
        if (!ModCompat.isLoaded("cold_sweat")) return false;
        for (MobEffectInstance effect : entity.getActiveEffects()) {
            ResourceLocation key = key(effect);
            if (key != null && key.getNamespace().equals("cold_sweat") && key.getPath().equals("grace")) return true;
        }
        return false;
    }

    public static ThermalProtection protection(LivingEntity entity) {
        if (!ModCompat.isLoaded("cold_sweat")) return ThermalProtection.NONE;
        ThermalProtection protection = ThermalProtection.NONE;
        for (MobEffectInstance effect : entity.getActiveEffects()) {
            ResourceLocation key = key(effect);
            if (key != null) protection = protection.plus(effect(key.getNamespace(), key.getPath()));
        }
        return protection;
    }

    /** One effect's protection under Cold Sweat's rules, or none. */
    public static ThermalProtection effect(String namespace, String path) {
        if (!ModCompat.isLoaded("cold_sweat") || !thermalPotion(namespace, path)) return ThermalProtection.NONE;
        return effect(namespace, path, setting("ICE_RESISTANCE_ENABLED"), setting("FIRE_RESISTANCE_ENABLED"));
    }

    static ThermalProtection effect(String namespace, String path, boolean iceEnabled, boolean fireEnabled) {
        if (iceEnabled && namespace.equals("cold_sweat") && path.equals("ice_resistance")) return ICE;
        if (fireEnabled && namespace.equals("minecraft") && path.equals("fire_resistance")) return FIRE;
        return ThermalProtection.NONE;
    }

    /** True when the effect counts as a thermal potion with Cold Sweat installed. */
    public static boolean thermalPotion(String namespace, String path) {
        return namespace.equals("cold_sweat") && path.equals("ice_resistance")
                || namespace.equals("minecraft") && path.equals("fire_resistance");
    }

    private static ResourceLocation key(MobEffectInstance effect) {
        //? if >=1.21 {
        return BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value());
        //?} else {
        /*return BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect());
        *///?}
    }

    /** A boolean setting from Cold Sweat's ConfigSettings; true, Cold Sweat's default, if unreadable. */
    private static boolean setting(String field) {
        Object holder = HOLDERS.computeIfAbsent(field, ColdSweatEntityCompat::holder);
        if (holder == MISSING) return true;
        try {
            return !Boolean.FALSE.equals(holder.getClass().getMethod("get").invoke(holder));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return true;
        }
    }

    private static final Object MISSING = new Object();
    private static final java.util.Map<String, Object> HOLDERS = new java.util.concurrent.ConcurrentHashMap<>();

    private static Object holder(String field) {
        try {
            Object holder = Class.forName("com.momosoftworks.coldsweat.config.ConfigSettings").getField(field).get(null);
            return holder == null ? MISSING : holder;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return MISSING;
        }
    }
}
