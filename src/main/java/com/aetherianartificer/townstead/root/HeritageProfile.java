package com.aetherianartificer.townstead.root;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * A data-authored name for a {@link Heritage} composition: matches a villager's
 * ancestry-fraction vector, its lineage shares, or both, and supplies the displayed
 * race name (e.g. "Half-Elf" for {human:½, elf:½}, or "Steam Drake" for a
 * {fire_drake:½, ice_drake:½} lineage mix within one ancestry). The
 * highest-{@link #priority} matching profile wins; when none match,
 * {@link HeritageRegistry} falls back to a pure ancestry's own name or a generated
 * blend label.
 *
 * <p>Loaded from {@code data/<ns>/heritage/<path>.json}: {@code "match"} bands
 * ancestry shares, {@code "lineages"} bands lineage shares, and {@code "genes"} lists
 * genes the heritage itself grants: expressed, on top of what was inherited, by
 * whoever this profile names (a Steam Drake's own breath).</p>
 */
public record HeritageProfile(
        ResourceLocation id,
        Component displayName,
        @Nullable Demonym demonym,
        @Nullable Component backstory,
        int priority,
        Map<ResourceLocation, GeneRange> match,
        Map<ResourceLocation, GeneRange> lineages,
        java.util.List<ResourceLocation> genes,
        /**
         * True ({@code "open_lineages": true}) when lineages the profile doesn't list may make up the
         * rest: a Half-Cat names a feline line crossed with any human, a plain one or a Primian. The
         * ancestry bands still bound who the other parent can be.
         */
        boolean openLineages
) {

    /** Heritage shares outside the listed ancestries may total at most this before a profile is rejected. */
    private static final float SLACK = 0.05f;

    public HeritageProfile {
        match = Map.copyOf(match);
        lineages = Map.copyOf(lineages);
        genes = java.util.List.copyOf(genes);
    }

    /** A profile whose unlisted lineages count against it. */
    public HeritageProfile(ResourceLocation id, Component displayName, @Nullable Demonym demonym,
                           @Nullable Component backstory, int priority, Map<ResourceLocation, GeneRange> match,
                           Map<ResourceLocation, GeneRange> lineages, java.util.List<ResourceLocation> genes) {
        this(id, displayName, demonym, backstory, priority, match, lineages, genes, false);
    }

    /** An ancestry-only profile that grants no genes. */
    public HeritageProfile(ResourceLocation id, Component displayName, @Nullable Demonym demonym,
                           @Nullable Component backstory, int priority, Map<ResourceLocation, GeneRange> match) {
        this(id, displayName, demonym, backstory, priority, match, Map.of(), java.util.List.of());
    }

    /** A profile that grants no genes. */
    public HeritageProfile(ResourceLocation id, Component displayName, @Nullable Demonym demonym,
                           @Nullable Component backstory, int priority, Map<ResourceLocation, GeneRange> match,
                           Map<ResourceLocation, GeneRange> lineages) {
        this(id, displayName, demonym, backstory, priority, match, lineages, java.util.List.of());
    }

    /**
     * True when every listed ancestry's and lineage's share sits in its band and the
     * villager carries no significant ancestry or lineage the profile doesn't mention
     * (so a pure elf never matches a half-elf profile, and a pure Fire Drake never a
     * Steam Drake one), unless its lineages are open. A side the profile leaves empty isn't checked.
     */
    public boolean matches(Heritage heritage) {
        if (match.isEmpty() && lineages.isEmpty()) return false;
        return within(match, heritage.fractions(), heritage::fractionOf, false)
                && within(lineages, heritage.lineages(), heritage::lineageFractionOf, openLineages);
    }

    private static boolean within(Map<ResourceLocation, GeneRange> bands, Map<ResourceLocation, Float> shares,
                                  java.util.function.Function<ResourceLocation, Float> shareOf, boolean open) {
        if (bands.isEmpty()) return true;
        float outside = 0f;
        for (Map.Entry<ResourceLocation, Float> e : shares.entrySet()) {
            if (!bands.containsKey(e.getKey())) outside += e.getValue();
        }
        if (!open && outside > SLACK) return false;
        for (Map.Entry<ResourceLocation, GeneRange> band : bands.entrySet()) {
            float share = shareOf.apply(band.getKey());
            if (share < band.getValue().min() || share > band.getValue().max()) return false;
        }
        return true;
    }
}
