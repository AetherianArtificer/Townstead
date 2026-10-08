package com.aetherianartificer.townstead.api.v1.model;

import net.minecraft.network.chat.Component;

/**
 * A personality definition. {@link #baseTemperament} is the MCA temperament it builds on, as a
 * lowercase name such as {@code confident} or {@code gloomy}.
 */
public record PersonalitySnapshot(
        String id,
        String baseTemperament,
        Component displayName,
        Component description
) {
}
