package com.aetherianartificer.townstead.api.v1.event;

import com.aetherianartificer.townstead.api.v1.model.SeatSnapshot;
import net.minecraft.server.MinecraftServer;

/** A political actor moved its Seat to another building. */
public record SeatMovedEvent(
        MinecraftServer server,
        SeatSnapshot before,
        SeatSnapshot after
) implements TownsteadEvent {
}
