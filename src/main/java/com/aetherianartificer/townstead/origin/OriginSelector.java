package com.aetherianartificer.townstead.origin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Chooses a founder's origin at spawn from the loaded origins, weighted by each
 * origin's {@link OriginRegistry#effectiveSpawnBias} at the spawn point. A
 * per-species {@code admixture_chance} then has a minority of founders come out
 * as a mixed-ancestry blend of two or more same-species origins instead.
 */
public final class OriginSelector {

    private OriginSelector() {}

    /** One origin's share of a mixed founder (fractions across a selection sum to 1). */
    public record Weighted(ResourceLocation originId, float fraction) {}

    /** Either a single chosen origin, or a mix of 2+ weighted same-species origins. */
    public record Selection(@Nullable ResourceLocation single, @Nullable List<Weighted> mix) {
        public boolean isMixed() {
            return mix != null && mix.size() > 1;
        }
    }

    /** Probability of adding each further origin to a mix beyond the first two. */
    private static final float MIX_GROW_CHANCE = 0.5f;

    public static Selection select(Level level, BlockPos pos, RandomSource random) {
        Selection s = select(level, pos, random, id -> true);
        // No filter applied: preserve the old "no origin matched -> default" guarantee.
        return s.single() == null && s.mix() == null ? new Selection(OriginRegistry.DEFAULT_ID, null) : s;
    }

    /**
     * As {@link #select(Level, BlockPos, RandomSource)} but constrained to origins {@code allowed}
     * accepts (the village/building spawn filter). Returns an empty selection (both fields null) when
     * nothing compatible passes, so the caller can substitute a guaranteed-compatible fallback.
     */
    public static Selection select(Level level, BlockPos pos, RandomSource random,
                                   Predicate<ResourceLocation> allowed) {
        Holder<Biome> biome = level.getBiome(pos);
        ResourceLocation biomeId = biome.unwrapKey().map(ResourceKey::location).orElse(null);
        Set<ResourceLocation> tagIds = new HashSet<>();
        biome.tags().forEach(t -> tagIds.add(t.location()));
        ResourceLocation dimId = level.dimension().location();

        ResourceLocation chosen = weightedPick(OriginRegistry.all(), biomeId, tagIds, dimId, random, allowed);
        if (chosen == null) return new Selection(null, null);

        ResourceLocation speciesId = OriginRegistry.effectiveSpecies(chosen);
        Species species = SpeciesRegistry.byId(speciesId);
        float chance = species == null ? 0f : species.admixtureChance();
        if (chance > 0f && random.nextFloat() < chance) {
            List<Weighted> mix = rollMix(speciesId, biomeId, tagIds, dimId, random, allowed);
            if (mix.size() > 1) return new Selection(null, mix);
        }
        return new Selection(chosen, null);
    }

    @Nullable
    private static ResourceLocation weightedPick(List<Origin> origins, @Nullable ResourceLocation biomeId,
                                                 Set<ResourceLocation> tagIds, @Nullable ResourceLocation dimId,
                                                 RandomSource random, Predicate<ResourceLocation> allowed) {
        if (origins.isEmpty()) return null;
        float[] weights = new float[origins.size()];
        float total = 0f;
        for (int i = 0; i < origins.size(); i++) {
            if (!allowed.test(origins.get(i).id())) continue;   // filtered out: weight stays 0
            float w = OriginRegistry.effectiveSpawnBias(origins.get(i).id()).weight(biomeId, tagIds, dimId);
            weights[i] = Math.max(0f, w);
            total += weights[i];
        }
        if (total <= 0f) return null;
        float roll = random.nextFloat() * total;
        ResourceLocation last = null;
        for (int i = 0; i < origins.size(); i++) {
            if (weights[i] <= 0f) continue;
            last = origins.get(i).id();
            roll -= weights[i];
            if (roll < 0f) return last;
        }
        return last;   // last positive-weight origin (float-rounding fallback; never a filtered-out one)
    }

    /**
     * Build a mixed founder within one species: pick 2..k distinct origins
     * (biome-weighted, without replacement) and give each an uneven random share.
     * Returns an empty list when the species has fewer than two origins.
     */
    private static List<Weighted> rollMix(@Nullable ResourceLocation speciesId, @Nullable ResourceLocation biomeId,
                                          Set<ResourceLocation> tagIds, @Nullable ResourceLocation dimId,
                                          RandomSource random, Predicate<ResourceLocation> allowed) {
        List<Origin> pool = new ArrayList<>();
        for (Origin o : OriginRegistry.all()) {
            if (allowed.test(o.id()) && Objects.equals(OriginRegistry.effectiveSpecies(o.id()), speciesId)) pool.add(o);
        }
        if (pool.size() < 2) return List.of();

        int count = 2;
        while (count < pool.size() && random.nextFloat() < MIX_GROW_CHANCE) count++;

        List<Origin> remaining = new ArrayList<>(pool);
        List<ResourceLocation> picked = new ArrayList<>(count);
        for (int n = 0; n < count && !remaining.isEmpty(); n++) {
            ResourceLocation pick = weightedPick(remaining, biomeId, tagIds, dimId, random, allowed);
            if (pick == null) pick = remaining.get(random.nextInt(remaining.size())).id();
            final ResourceLocation chosen = pick;
            picked.add(chosen);
            remaining.removeIf(o -> o.id().equals(chosen));
        }
        if (picked.size() < 2) return List.of();

        float[] raw = new float[picked.size()];
        float sum = 0f;
        for (int i = 0; i < raw.length; i++) {
            raw[i] = 0.1f + random.nextFloat();
            sum += raw[i];
        }
        List<Weighted> mix = new ArrayList<>(picked.size());
        for (int i = 0; i < picked.size(); i++) mix.add(new Weighted(picked.get(i), raw[i] / sum));
        return mix;
    }
}
