package com.aetherianartificer.townstead.api.v1.event;

import com.aetherianartificer.townstead.api.v1.model.SettlementFoundingSnapshot;
import net.minecraft.server.MinecraftServer;

/** A settlement's founding result was first recorded. */
public record SettlementFoundedEvent(
        MinecraftServer server,
        SettlementFoundingSnapshot founding
) implements TownsteadEvent {
}
