package com.aetherianartificer.townstead.api.v1.event;

import com.aetherianartificer.townstead.api.v1.model.PolitySnapshot;
import net.minecraft.server.MinecraftServer;

/** A polity's name, color or emblem changed. */
public record PolityIdentityChangedEvent(
        MinecraftServer server,
        PolitySnapshot before,
        PolitySnapshot after
) implements TownsteadEvent {
}
