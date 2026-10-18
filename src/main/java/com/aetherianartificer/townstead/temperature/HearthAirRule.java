package com.aetherianartificer.townstead.temperature;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;

/**
 * While room heat runs, a Cold Sweat hearth heats or cools the room's air like any appliance, and
 * players and villagers feel it through that air. Its own Warmth and Frigidness would count it a
 * second time, so the ones a hearth hands out are not applied. Warmth from anything else still is.
 */
public final class HearthAirRule {
    private static final ResourceLocation WARMTH = ResourceLocation.tryParse("cold_sweat:warmth");
    private static final ResourceLocation FRIGIDNESS = ResourceLocation.tryParse("cold_sweat:frigidness");
    private static final ThreadLocal<Integer> INSULATING = ThreadLocal.withInitial(() -> 0);

    private HearthAirRule() {}

    public static boolean active() {
        return TemperatureSettings.get().roomHeatEnabled();
    }

    public static void enter() {
        INSULATING.set(INSULATING.get() + 1);
    }

    public static void leave() {
        INSULATING.set(Math.max(0, INSULATING.get() - 1));
    }

    /** Whether this effect, about to be applied, is a hearth's that room air replaces. */
    public static boolean blocks(MobEffect effect) {
        if (INSULATING.get() == 0 || !active()) return false;
        ResourceLocation id = BuiltInRegistries.MOB_EFFECT.getKey(effect);
        return WARMTH.equals(id) || FRIGIDNESS.equals(id);
    }
}
