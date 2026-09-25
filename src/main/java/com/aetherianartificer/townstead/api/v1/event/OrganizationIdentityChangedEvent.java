package com.aetherianartificer.townstead.api.v1.event;

import com.aetherianartificer.townstead.api.v1.model.OrganizationSnapshot;
import net.minecraft.server.MinecraftServer;

/** An organization's name, short name, color or emblem changed. */
public record OrganizationIdentityChangedEvent(
        MinecraftServer server,
        OrganizationSnapshot before,
        OrganizationSnapshot after
) implements TownsteadEvent {
}
