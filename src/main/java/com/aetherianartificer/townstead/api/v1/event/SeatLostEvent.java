package com.aetherianartificer.townstead.api.v1.event;

import com.aetherianartificer.townstead.api.v1.model.SeatSnapshot;
import net.minecraft.server.MinecraftServer;

/**
 * A political actor lost its Seat. {@code reason} is {@code charter_removed} (the Charter was
 * unbound) or {@code actor_dissolved}. A broken building or lectern damages a Seat instead; see
 * {@link SeatDamagedEvent}.
 */
public record SeatLostEvent(
        MinecraftServer server,
        SeatSnapshot seat,
        String reason
) implements TownsteadEvent {
}
