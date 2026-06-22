package com.aetherianartificer.townstead.api;

/** Serializable read-only needs state for one Townstead villager. */
public record TownsteadNeedsSnapshot(
        int hunger,
        float saturation,
        float hungerExhaustion,
        int thirst,
        int quenched,
        float thirstExhaustion,
        int fatigue,
        boolean collapsed,
        boolean gated
) {}
