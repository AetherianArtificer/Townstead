package com.aetherianartificer.townstead.origin;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * A data-authored name for a {@link Heritage} composition: matches a villager's
 * ancestry-fraction vector and supplies the displayed race name (e.g. "Half-Elf"
 * for {human:½, elf:½}). The highest-{@link #priority} matching profile wins; when
 * none match, {@link HeritageRegistry} falls back to a pure ancestry's own name or
 * a generated blend label.
 *
 * <p>Loaded from {@code data/<ns>/heritage/<path>.json}.</p>
 */
public record HeritageProfile(
        ResourceLocation id,
        Component displayName,
        @Nullable Demonym demonym,
        @Nullable Component backstory,
        int priority,
        Map<ResourceLocation, GeneRange> match
) {

    /** Heritage shares outside the listed ancestries may total at most this before a profile is rejected. */
    private static final float SLACK = 0.05f;

    public HeritageProfile {
        match = Map.copyOf(match);
    }

    /**
     * True when every listed ancestry's fraction sits in its band and the villager
     * carries no significant ancestry the profile doesn't mention (so a pure elf
     * never matches a half-elf profile).
     */
    public boolean matches(Heritage heritage) {
        if (match.isEmpty()) return false;
        float outside = 0f;
        for (Map.Entry<ResourceLocation, Float> e : heritage.fractions().entrySet()) {
            if (!match.containsKey(e.getKey())) outside += e.getValue();
        }
        if (outside > SLACK) return false;
        for (Map.Entry<ResourceLocation, GeneRange> band : match.entrySet()) {
            float share = heritage.fractionOf(band.getKey());
            if (share < band.getValue().min() || share > band.getValue().max()) return false;
        }
        return true;
    }
}
