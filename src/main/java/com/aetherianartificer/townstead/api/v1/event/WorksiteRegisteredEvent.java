package com.aetherianartificer.townstead.api.v1.event;

import com.aetherianartificer.townstead.api.v1.model.WorksiteSnapshot;
import net.minecraft.server.MinecraftServer;

/** A worksite was registered. */
public record WorksiteRegisteredEvent(
        MinecraftServer server,
        WorksiteSnapshot worksite
) implements TownsteadEvent {
}
