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
                                      Optional<ResourceLocation> governmentOrganizationKind,
                                      Optional<String> governmentNamePattern,
                                      List<GovernmentSeat> governmentSeats) {
    public FoundingProfileSnapshot {
        culture = culture == null ? Optional.empty() : culture;
        defaultEnvironmentWeight = defaultEnvironmentWeight == null ? Optional.empty() : defaultEnvironmentWeight;
        governmentOrganizationKind = governmentOrganizationKind == null ? Optional.empty() : governmentOrganizationKind;
        governmentNamePattern = governmentNamePattern == null ? Optional.empty() : governmentNamePattern;
        biomeWeights = Map.copyOf(biomeWeights);
        biomeTagWeights = Map.copyOf(biomeTagWeights);
        dimensionWeights = Map.copyOf(dimensionWeights);
        rootAdjustments = Map.copyOf(rootAdjustments);
        governmentSeats = List.copyOf(governmentSeats);
    }

    public record GovernmentSeat(Set<ResourceLocation> roles, int count) {
        public GovernmentSeat {
            roles = Set.copyOf(roles);
        }
    }
}
