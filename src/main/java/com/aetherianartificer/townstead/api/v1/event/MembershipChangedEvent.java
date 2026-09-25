package com.aetherianartificer.townstead.api.v1.event;

import com.aetherianartificer.townstead.api.v1.model.MembershipSnapshot;
import net.minecraft.server.MinecraftServer;

import java.util.Optional;

/** A membership was created, or its status or roles changed; before is empty on creation. */
public record MembershipChangedEvent(
        MinecraftServer server,
        Optional<MembershipSnapshot> before,
        MembershipSnapshot after
) implements TownsteadEvent {
}
