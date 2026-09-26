package com.aetherianartificer.townstead.api.v1.event;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/**
 * A government's legitimacy crossed into another band: {@code resented}, {@code uneasy},
 * {@code tolerated}, {@code accepted}, or {@code beloved}. {@code value} is 0 to 100.
 */
public record LegitimacyChangedEvent(
        MinecraftServer server,
        ResourceLocation polity,
        ResourceLocation government,
        String before,
        String after,
        int value
) implements TownsteadEvent {
}
