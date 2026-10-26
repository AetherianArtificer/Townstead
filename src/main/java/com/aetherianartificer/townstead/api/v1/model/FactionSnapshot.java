package com.aetherianartificer.townstead.api.v1.model;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

/**
 * One faction: a village, a player's realm, a guild. {@code kind} is its faction kind, such as
 * {@code townstead:village_council}. {@code settlements} are the places whose town range it holds;
 * a landless faction has none and names its {@code home}. {@code parent} is the faction it gives its
 * land to, empty for a sovereign. Claim geometry belongs to Warstead, not this record.
 */
public record FactionSnapshot(ResourceLocation id,
                              ResourceLocation kind,
                              String name,
                              int color,
                              Optional<ResourceLocation> emblem,
                              long createdAt,
                              ResourceLocation provenance,
                              String status,
                              List<VillageId> settlements,
                              Optional<VillageId> home,
                              Optional<ResourceLocation> parent,
                              boolean holdsLand,
                              int memberCount) {
    public FactionSnapshot {
        settlements = List.copyOf(settlements);
    }
}
