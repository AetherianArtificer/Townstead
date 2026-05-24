package com.aetherianartificer.townstead.origin;

import com.aetherianartificer.townstead.data.DataPackLang;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Server-side registry of data-pack-loaded {@link Origin}, plus the genome
 * resolution that composes species/ancestry/heritage into the effective genome
 * a villager is given. Populated by {@link OriginJsonLoader}.
 */
public final class OriginRegistry {

    /** Namespace shared by all built-in origin data ({@code townstead_origins}). */
    public static final String NAMESPACE = "townstead_origins";

    /** The always-present built-in origin used as the fallback everywhere. */
    public static final ResourceLocation DEFAULT_ID =
            Objects.requireNonNull(DataPackLang.parseId(NAMESPACE + ":overworlder"));

    private static volatile Map<ResourceLocation, Origin> ENTRIES = Map.of();

    private OriginRegistry() {}

    static void replaceAll(Map<ResourceLocation, Origin> next) {
        ENTRIES = Map.copyOf(new LinkedHashMap<>(next));
    }

    @Nullable
    public static Origin byId(ResourceLocation id) {
        return id == null ? null : ENTRIES.get(id);
    }

    public static List<Origin> all() {
        return List.copyOf(ENTRIES.values());
    }

    public static int size() {
        return ENTRIES.size();
    }

    /** The given origin, or the default ({@link #DEFAULT_ID}) if it is missing. */
    @Nullable
    public static Origin resolveOrDefault(@Nullable ResourceLocation id) {
        if (id != null) {
            Origin direct = ENTRIES.get(id);
            if (direct != null) return direct;
        }
        return ENTRIES.get(DEFAULT_ID);
    }

    /** Effective genome for an origin id, falling back to the default origin. */
    public static Genome effectiveGenome(@Nullable ResourceLocation id) {
        Origin origin = resolveOrDefault(id);
        return origin == null ? Genome.EMPTY : effectiveGenome(origin);
    }

    /**
     * Compose the genome bottom-up: ancestry (or, for a heritage-based origin,
     * the union of the heritage's ancestries then its overrides) → the origin's
     * own overrides. Per-gene entries replace; tag lists union. Missing refs are
     * skipped (they leave the base un-narrowed).
     */
    public static Genome effectiveGenome(Origin origin) {
        Genome base = Genome.EMPTY;
        if (origin.heritage() != null) {
            Heritage heritage = HeritageRegistry.byId(origin.heritage());
            if (heritage != null) {
                for (ResourceLocation ancestryId : heritage.ancestries()) {
                    Ancestry ancestry = AncestryRegistry.byId(ancestryId);
                    if (ancestry != null) base = base.mergedWith(ancestry.genome());
                }
                base = base.mergedWith(heritage.genomeOverrides());
            }
        } else if (origin.ancestry() != null) {
            Ancestry ancestry = AncestryRegistry.byId(origin.ancestry());
            if (ancestry != null) base = ancestry.genome();
        }
        return base.mergedWith(origin.genomeOverrides());
    }

    /** The origin's demonym, falling back to its heritage's then its ancestry's. */
    @Nullable
    public static Demonym resolveDemonym(Origin origin) {
        if (origin.demonym() != null) return origin.demonym();
        if (origin.heritage() != null) {
            Heritage heritage = HeritageRegistry.byId(origin.heritage());
            if (heritage != null && heritage.demonym() != null) return heritage.demonym();
        }
        if (origin.ancestry() != null) {
            Ancestry ancestry = AncestryRegistry.byId(origin.ancestry());
            if (ancestry != null && ancestry.demonym() != null) return ancestry.demonym();
        }
        return null;
    }

    /** The origin's backstory, falling back to its heritage's then its ancestry's. */
    @Nullable
    public static Component resolveBackstory(Origin origin) {
        if (origin.backstory() != null) return origin.backstory();
        if (origin.heritage() != null) {
            Heritage heritage = HeritageRegistry.byId(origin.heritage());
            if (heritage != null && heritage.backstory() != null) return heritage.backstory();
        }
        if (origin.ancestry() != null) {
            Ancestry ancestry = AncestryRegistry.byId(origin.ancestry());
            if (ancestry != null && ancestry.backstory() != null) return ancestry.backstory();
        }
        return null;
    }
}
