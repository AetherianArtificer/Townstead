package com.aetherianartificer.townstead.compat.vampirism;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelAccessor;

import java.lang.reflect.Method;

/**
 * Garlic works on vampire villagers as it does on Vampirism's own vampires: in a chunk a garlic
 * diffuser covers, they take Vampirism's garlic effect (and blindness where it is strong), and walking
 * through ripe garlic gives the same weak dose Vampirism gives its own vampires. Keeping out of it is
 * the {@code vampire_garlic_aversion} gene's hazard avoidance. Reflection only.
 */
public final class VampireGarlic {
    private static final ResourceLocation GARLIC = ResourceLocation.tryParse("vampirism:garlic");
    private static final int INTERVAL = 20;
    private static final int EFFECT_TICKS = 100;
    private static volatile boolean resolved;
    private static volatile Method strengthAt;
    private static volatile Method strengthValue;

    private VampireGarlic() {}

    /** Vampirism's direct contact: twenty seconds of weak garlic. */
    private static final int CONTACT_TICKS = 400;

    public static void tick(VillagerEntityMCA villager) {
        if ((villager.tickCount + villager.getId()) % INTERVAL != 0 || !VampireVillagers.isVampire(villager)) return;
        if (inRipeGarlic(villager)) garlic(villager, CONTACT_TICKS, 0);
        int strength = strength(villager);
        if (strength <= 0) return;
        //? if neoforge {
        BuiltInRegistries.MOB_EFFECT.getHolder(net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.MOB_EFFECT, GARLIC))
                .ifPresent(effect -> villager.addEffect(new MobEffectInstance(effect, EFFECT_TICKS, strength - 1, true, true)));
        //?} else {
        /*var effect = BuiltInRegistries.MOB_EFFECT.get(GARLIC);
        if (effect != null) villager.addEffect(new MobEffectInstance(effect, EFFECT_TICKS, strength - 1, true, true));
        *///?}
        if (strength >= 3) villager.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, EFFECT_TICKS / 2, 0, true, false));
    }

    /** Standing in a garlic crop past age 5, the point at which Vampirism's own vampires are hurt by it. */
    private static boolean inRipeGarlic(VillagerEntityMCA villager) {
        var state = villager.level().getBlockState(villager.blockPosition());
        if (!GARLIC.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()))) return false;
        for (var property : state.getProperties()) {
            if ("age".equals(property.getName()) && state.getValue(property) instanceof Integer age) return age > 5;
        }
        return false;
    }

    private static void garlic(VillagerEntityMCA villager, int ticks, int amplifier) {
        //? if neoforge {
        BuiltInRegistries.MOB_EFFECT.getHolder(net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.MOB_EFFECT, GARLIC))
                .ifPresent(effect -> villager.addEffect(new MobEffectInstance(effect, ticks, amplifier, false, true)));
        //?} else {
        /*var effect = BuiltInRegistries.MOB_EFFECT.get(GARLIC);
        if (effect != null) villager.addEffect(new MobEffectInstance(effect, ticks, amplifier, false, true));
        *///?}
    }

    private static int strength(Entity entity) {
        if (!resolve()) return 0;
        try {
            Object value = strengthAt.invoke(null, entity, entity.level());
            return value == null ? 0 : (int) strengthValue.invoke(value);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static boolean resolve() {
        if (resolved) return strengthAt != null;
        try {
            Class<?> helper = Class.forName("de.teamlapen.vampirism.util.Helper");
            strengthAt = helper.getMethod("getGarlicStrength", Entity.class, LevelAccessor.class);
            strengthValue = Class.forName("de.teamlapen.vampirism.api.EnumStrength").getMethod("getStrength");
        } catch (Throwable ignored) {
            strengthAt = null;
        }
        resolved = true;
        return strengthAt != null;
    }
}
