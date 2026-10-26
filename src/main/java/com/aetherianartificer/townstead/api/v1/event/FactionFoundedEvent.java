package com.aetherianartificer.townstead.api.v1.event;

import com.aetherianartificer.townstead.api.v1.model.FactionSnapshot;
import net.minecraft.server.MinecraftServer;

/** A new faction was recorded: a village recognized, a player founding, or a converted older record. */
public record FactionFoundedEvent(
        MinecraftServer server,
        FactionSnapshot faction
) implements TownsteadEvent {
}
