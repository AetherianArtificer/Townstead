package com.aetherianartificer.townstead.api.v1.event;

import com.aetherianartificer.townstead.api.v1.model.OrganizationSnapshot;
import net.minecraft.server.MinecraftServer;

/** A new political organization was recorded. */
public record OrganizationFoundedEvent(
        MinecraftServer server,
        OrganizationSnapshot organization
) implements TownsteadEvent {
}
