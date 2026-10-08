package com.aetherianartificer.townstead.api.v1.event;

import com.aetherianartificer.townstead.api.v1.model.SeatSnapshot;
import net.minecraft.server.MinecraftServer;

/** A damaged Seat was rebuilt and provides its functions again. */
public record SeatRepairedEvent(
        MinecraftServer server,
        SeatSnapshot seat
) implements TownsteadEvent {
}
