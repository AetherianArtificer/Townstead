package com.aetherianartificer.townstead.api.v1.event;

import com.aetherianartificer.townstead.api.v1.model.VillageId;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import java.util.Optional;

/** A settlement joined, left or moved between polities. */
public record SettlementPolityChangedEvent(
        MinecraftServer server,
        VillageId settlement,
        Optional<ResourceLocation> before,
        Optional<ResourceLocation> after
) implements TownsteadEvent {
}
