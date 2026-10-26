package com.aetherianartificer.townstead.api.v1.event;

import com.aetherianartificer.townstead.api.v1.model.FactionSnapshot;
import net.minecraft.server.MinecraftServer;

/** A faction's name, color or emblem changed. */
public record FactionIdentityChangedEvent(
        MinecraftServer server,
        FactionSnapshot before,
        FactionSnapshot after
) implements TownsteadEvent {
}
