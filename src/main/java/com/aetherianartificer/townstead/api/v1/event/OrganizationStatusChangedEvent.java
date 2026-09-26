package com.aetherianartificer.townstead.api.v1.event;

import com.aetherianartificer.townstead.api.v1.model.OrganizationSnapshot;
import net.minecraft.server.MinecraftServer;

/** An organization's status changed between active, dormant and dissolved. */
public record OrganizationStatusChangedEvent(
        MinecraftServer server,
        OrganizationSnapshot before,
        OrganizationSnapshot after
) implements TownsteadEvent {
}
