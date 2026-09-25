package com.aetherianartificer.townstead.api.v1.event;

import com.aetherianartificer.townstead.api.v1.model.PolitySnapshot;
import net.minecraft.server.MinecraftServer;

/** A polity's status changed between active, dormant and dissolved. */
public record PolityStatusChangedEvent(
        MinecraftServer server,
        PolitySnapshot before,
        PolitySnapshot after
) implements TownsteadEvent {
}
