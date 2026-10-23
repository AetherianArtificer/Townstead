package com.aetherianartificer.townstead.api.v1.event;

import com.aetherianartificer.townstead.api.v1.model.FactionSnapshot;
import net.minecraft.server.MinecraftServer;

/** A faction's status changed between {@code active}, {@code dormant} and {@code dissolved}. */
public record FactionStatusChangedEvent(
        MinecraftServer server,
        FactionSnapshot before,
        FactionSnapshot after
) implements TownsteadEvent {
}
