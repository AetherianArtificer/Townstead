package com.aetherianartificer.townstead.performance;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

import java.util.Objects;

/** A semantic performance request. Providers decide whether that means an emote, model clip, or JSON animation. */
public record PerformanceRequest(LivingEntity actor, ResourceLocation performance, String channel,
                                 int durationTicks, int priority, Fallback fallback) {
    public enum Fallback { VANILLA_GESTURE, STAND, NONE }

    public PerformanceRequest {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(performance, "performance");
        if (channel == null || channel.isBlank()) throw new IllegalArgumentException("channel is required");
        if (durationTicks < 1) throw new IllegalArgumentException("durationTicks must be positive");
        fallback = fallback == null ? Fallback.VANILLA_GESTURE : fallback;
    }
}
