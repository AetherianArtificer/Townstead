package com.aetherianartificer.townstead.api.v1.event;

import com.aetherianartificer.townstead.api.v1.model.PolitySnapshot;
import net.minecraft.server.MinecraftServer;

/** A new polity was recorded. */
public record PolityFoundedEvent(
        MinecraftServer server,
        PolitySnapshot polity
) implements TownsteadEvent {
}
