package com.aetherianartificer.townstead.api.v1.event;

import com.aetherianartificer.townstead.api.v1.model.SeatSnapshot;
import net.minecraft.server.MinecraftServer;

/**
 * A Seat was damaged: its building no longer stands ({@code building_missing}) or its Charter
 * lectern is gone ({@code lectern_missing}). The Seat keeps its place and provides no functions
 * until it is repaired.
 */
public record SeatDamagedEvent(
        MinecraftServer server,
        SeatSnapshot seat,
        String reason
) implements TownsteadEvent {
}
