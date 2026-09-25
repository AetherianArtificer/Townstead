package com.aetherianartificer.townstead.api.v1.event;

import com.aetherianartificer.townstead.api.v1.model.AffiliationSnapshot;
import net.minecraft.server.MinecraftServer;

import java.util.Optional;

/** A non-membership affiliation was created or its status changed; before is empty on creation. */
public record AffiliationChangedEvent(
        MinecraftServer server,
        Optional<AffiliationSnapshot> before,
        AffiliationSnapshot after
) implements TownsteadEvent {
}
