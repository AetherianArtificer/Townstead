package com.aetherianartificer.townstead.api.v1.event;

import com.aetherianartificer.townstead.api.v1.model.ChronicleEventView;
import net.minecraft.server.MinecraftServer;

/** A ground-truth chronicle event was recorded, whatever its source. */
public record ChronicleEventRecordedEvent(
        MinecraftServer server,
        ChronicleEventView event
) implements TownsteadEvent {
}
