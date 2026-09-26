package com.aetherianartificer.townstead.api.v1.model;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

/** Immutable public identity of one polity; it intentionally contains no claim geometry. */
public record PolitySnapshot(ResourceLocation id,
                            String name,
                            int color,
                            Optional<ResourceLocation> emblem,
                            long createdAt,
                            ResourceLocation provenance,
                            String status,
                            List<VillageId> settlements,
                            Optional<ResourceLocation> governmentOrganization) {
    public PolitySnapshot {
        settlements = List.copyOf(settlements);
    }
}
