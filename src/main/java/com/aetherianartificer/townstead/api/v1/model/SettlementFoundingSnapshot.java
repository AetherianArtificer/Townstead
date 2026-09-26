package com.aetherianartificer.townstead.api.v1.model;

import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/** Persisted founding result for a settlement; definitions may reload without changing this record. */
public record SettlementFoundingSnapshot(VillageId settlement,
                                         ResourceLocation profile,
                                         Optional<ResourceLocation> culture,
                                         Optional<ResourceLocation> government,
                                         Optional<ResourceLocation> foundingBiome,
                                         float naturalWeight,
                                         long foundedAt) {
    public SettlementFoundingSnapshot {
        culture = culture == null ? Optional.empty() : culture;
        government = government == null ? Optional.empty() : government;
        foundingBiome = foundingBiome == null ? Optional.empty() : foundingBiome;
    }
}
