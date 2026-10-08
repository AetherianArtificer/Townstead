package com.aetherianartificer.townstead.api.v1.model;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Immutable data-pack definition used to found or generate a settlement identity. */
public record FoundingProfileSnapshot(ResourceLocation id,
                                      String displayName,
                                      Optional<ResourceLocation> culture,
                                      float weight,
                                      Optional<Float> defaultEnvironmentWeight,
                                      Map<ResourceLocation, Float> biomeWeights,
                                      Map<ResourceLocation, Float> biomeTagWeights,
                                      Map<ResourceLocation, Float> dimensionWeights,
                                      ResourceLocation populationStrategy,
                                      float outsiderBaseline,
                                      Map<ResourceLocation, Float> rootAdjustments,
                                      Optional<ResourceLocation> factionKind,
                                      List<Seat> seats) {
    public FoundingProfileSnapshot {
        culture = culture == null ? Optional.empty() : culture;
        defaultEnvironmentWeight = defaultEnvironmentWeight == null ? Optional.empty() : defaultEnvironmentWeight;
        factionKind = factionKind == null ? Optional.empty() : factionKind;
        biomeWeights = Map.copyOf(biomeWeights);
        biomeTagWeights = Map.copyOf(biomeTagWeights);
        dimensionWeights = Map.copyOf(dimensionWeights);
        rootAdjustments = Map.copyOf(rootAdjustments);
        seats = List.copyOf(seats);
    }

    /** {@code count} residents each take this set of offices when the settlement is founded. */
    public record Seat(Set<ResourceLocation> offices, int count) {
        public Seat {
            offices = Set.copyOf(offices);
        }
    }
}
