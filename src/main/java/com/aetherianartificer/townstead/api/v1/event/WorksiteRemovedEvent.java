package com.aetherianartificer.townstead.api.v1.event;

import com.aetherianartificer.townstead.api.v1.model.WorksiteSnapshot;
import net.minecraft.server.MinecraftServer;

/** A worksite was removed from the register. */
public record WorksiteRemovedEvent(
        MinecraftServer server,
        WorksiteSnapshot worksite
) implements TownsteadEvent {
}
