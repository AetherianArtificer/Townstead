package com.aetherianartificer.townstead.api.v1.event;

import com.aetherianartificer.townstead.api.v1.model.FactionSnapshot;
import net.minecraft.server.MinecraftServer;

/** A faction became another kind, as when a settlement is refounded under a new form of government. */
public record FactionKindChangedEvent(
        MinecraftServer server,
        FactionSnapshot before,
        FactionSnapshot after
) implements TownsteadEvent {
}
