package com.aetherianartificer.townstead.origin;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Per-biome / per-dimension founder weighting, authored on ancestries, lineages
 * and selectable assignment profiles and composed into one effective bias by
 * {@link OriginRegistry#effectiveSpawnBias}. A weight is resolved most-specific
 * first: exact biome, then any matching biome tag, then dimension, then the
 * {@code default} (1.0 when unset). An empty bias is a flat weight of 1.0
 * everywhere, so a profile with no {@code spawn_bias} is a uniform baseline.
 *
 * <p>Authored as {@code spawn_bias: { default, biomes{}, biome_tags{}, dimensions{} }}.</p>
 */
public record SpawnBias(@Nullable Float defaultWeight,
                        Map<ResourceLocation, Float> biomes,
                        Map<ResourceLocation, Float> biomeTags,
                        Map<ResourceLocation, Float> dimensions) {

    public static final SpawnBias EMPTY = new SpawnBias(null, Map.of(), Map.of(), Map.of());

    public SpawnBias {
        biomes = biomes == null ? Map.of() : Map.copyOf(biomes);
        biomeTags = biomeTags == null ? Map.of() : Map.copyOf(biomeTags);
        dimensions = dimensions == null ? Map.of() : Map.copyOf(dimensions);
    }

    public boolean isEmpty() {
        return defaultWeight == null && biomes.isEmpty() && biomeTags.isEmpty() && dimensions.isEmpty();
    }

    /**
     * The weight at a spawn point. Exact biome wins; else the heaviest matching
     * biome tag; else the dimension; else the default (1.0 when unset).
     */
    public float weight(@Nullable ResourceLocation biomeId, @Nullable Set<ResourceLocation> biomeTagIds,
                        @Nullable ResourceLocation dimId) {
        if (biomeId != null) {
            Float w = biomes.get(biomeId);
            if (w != null) return w;
        }
        if (!biomeTags.isEmpty() && biomeTagIds != null) {
            float best = Float.NaN;
            for (ResourceLocation tag : biomeTagIds) {
                Float w = biomeTags.get(tag);
                if (w != null && (Float.isNaN(best) || w > best)) best = w;
            }
            if (!Float.isNaN(best)) return best;
        }
        if (dimId != null) {
            Float w = dimensions.get(dimId);
            if (w != null) return w;
        }
        return defaultWeight != null ? defaultWeight : 1.0f;
    }

    /** Compose: {@code this} is the base, {@code over} overrides per key and replaces the default if it sets one. */
    public SpawnBias mergedWith(@Nullable SpawnBias over) {
        if (over == null || over.isEmpty()) return this;
        Map<ResourceLocation, Float> b = new LinkedHashMap<>(this.biomes);
        b.putAll(over.biomes);
        Map<ResourceLocation, Float> t = new LinkedHashMap<>(this.biomeTags);
        t.putAll(over.biomeTags);
        Map<ResourceLocation, Float> d = new LinkedHashMap<>(this.dimensions);
        d.putAll(over.dimensions);
        Float def = over.defaultWeight != null ? over.defaultWeight : this.defaultWeight;
        return new SpawnBias(def, b, t, d);
    }
}
