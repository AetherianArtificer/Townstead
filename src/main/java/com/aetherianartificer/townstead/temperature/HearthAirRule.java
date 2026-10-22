package com.aetherianartificer.townstead.temperature;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayDeque;

/**
 * While Townstead owns block heat, a Cold Sweat hearth is an appliance like any other: it heats its
 * room's air and radiates to bodies in sight. Its own Warmth and Frigidness would count it a second
 * time, so the ones a hearth hands out are not applied. Warmth from anything else still applies.
 */
public final class HearthAirRule {
    private static final ResourceLocation WARMTH = ResourceLocation.tryParse("cold_sweat:warmth");
    private static final ResourceLocation FRIGIDNESS = ResourceLocation.tryParse("cold_sweat:frigidness");
    private static final ThreadLocal<ArrayDeque<Boolean>> INSULATING = ThreadLocal.withInitial(ArrayDeque::new);

    private HearthAirRule() {}

    public static boolean active() {
        return com.aetherianartificer.townstead.compat.temperature.RoomHeatBackend.ownsBlockHeat();
    }

    public static void enter(LivingEntity entity) {
        INSULATING.get().push(active());
    }

    public static void leave() {
        INSULATING.get().poll();
    }

    /** Whether this effect, about to be applied, is a hearth's that room air replaces. */
    public static boolean blocks(MobEffect effect) {
        if (!Boolean.TRUE.equals(INSULATING.get().peek())) return false;
        ResourceLocation id = BuiltInRegistries.MOB_EFFECT.getKey(effect);
        return WARMTH.equals(id) || FRIGIDNESS.equals(id);
    }
}
