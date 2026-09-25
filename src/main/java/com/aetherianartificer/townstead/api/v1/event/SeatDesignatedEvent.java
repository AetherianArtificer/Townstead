package com.aetherianartificer.townstead.api.v1.event;

import com.aetherianartificer.townstead.api.v1.model.SeatSnapshot;
import net.minecraft.server.MinecraftServer;

/** A political actor that had no Seat designated one. */
public record SeatDesignatedEvent(
        MinecraftServer server,
        SeatSnapshot seat
) implements TownsteadEvent {
}
