package com.aetherianartificer.townstead.compat.temperature;

import com.aetherianartificer.townstead.temperature.ThermalProtection;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;

/** Player-only LSO attribute effects expressed in Townstead's villager model. */
public final class LsoEntityCompat {
    private LsoEntityCompat() {}

    public record Effects(float ambientOffset, ThermalProtection protection) {}

    public static Effects effects(LivingEntity entity) {
        float offset = 0;
        ThermalProtection protection = ThermalProtection.NONE;
        for (var effect : entity.getActiveEffects()) {
            //? if >=1.21 {
            var id = BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value());
            //?} else {
            /*var id = BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect());
            *///?}
            if (id == null || !id.getNamespace().equals("legendarysurvivaloverhaul")) continue;
            Effects value = effect(id.getPath(), effect.getAmplifier());
            offset += value.ambientOffset();
            protection = protection.plus(value.protection());
        }
        return new Effects(offset, protection);
    }

    static Effects effect(String path, int amplifier) {
        float level = Math.max(0, (float) amplifier + 1);
        return switch (path) {
            case "hot_food", "hot_drink" -> new Effects(level, ThermalProtection.NONE);
            case "cold_food", "cold_drink" -> new Effects(-level, ThermalProtection.NONE);
            case "cold_resistance" -> new Effects(0, new ThermalProtection(0, level, 0, 0));
            case "heat_resistance" -> new Effects(0, new ThermalProtection(0, 0, level, 0));
            case "cold_immunity" -> new Effects(0, new ThermalProtection(0, Float.MAX_VALUE, 0, 0));
            case "heat_immunity" -> new Effects(0, new ThermalProtection(0, 0, Float.MAX_VALUE, 0));
            case "temperature_immunity" -> new Effects(0, new ThermalProtection(0, 0, 0, Float.MAX_VALUE));
            default -> new Effects(0, ThermalProtection.NONE);
        };
    }
}
