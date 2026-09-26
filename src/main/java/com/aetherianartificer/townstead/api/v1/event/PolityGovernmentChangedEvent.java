package com.aetherianartificer.townstead.api.v1.event;

import com.aetherianartificer.townstead.api.v1.model.PolitySnapshot;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import java.util.Optional;

/** A polity's bound government organization changed. */
public record PolityGovernmentChangedEvent(
        MinecraftServer server,
        PolitySnapshot polity,
        Optional<ResourceLocation> before,
        Optional<ResourceLocation> after
) implements TownsteadEvent {
}
