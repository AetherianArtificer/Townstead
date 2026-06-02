package com.aetherianartificer.townstead.origin.chronotype;

import com.aetherianartificer.townstead.origin.gene.Gene;
import com.aetherianartificer.townstead.origin.gene.GeneRegistry;
import com.aetherianartificer.townstead.origin.gene.GeneVariant;
import com.aetherianartificer.townstead.origin.gene.types.ChronotypeGeneType;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Resolves a villager's chronotype from the chronotype gene it carries. A race grants
 * one chronotype gene (they share the {@link #LOCUS} slot); the villager carries the
 * variant rolled at birth. Expression is derived (never frozen): read the carried variant
 * id off the gene that sits at the locus and look up its sleep window. Legacy villagers have
 * their chronotype rolled once on load ({@code OriginSpawnHandler.backfillIfMissing}), so every
 * villager ends up purely gene-driven.
 */
public final class Chronotypes {

    /** Shared locus every chronotype gene occupies (one per villager). */
    public static final String LOCUS = "townstead_origins:chronotypes";
    public static final String DEFAULT_VARIANT = "standard";

    /** Standard 11 PM .. 7 AM as tick-hours (0 == 6 AM); used only when nothing resolves. */
    private static final int[] DEFAULT_SLEEP = {17, 18, 19, 20, 21, 22, 23, 0};

    private Chronotypes() {}

    /** A resolved chronotype: the expressed variant id, its label, and sleep window (tick-hours). */
    public record Resolved(String id, String label, int[] sleepHours) {
        public boolean isPreferredSleepHour(int tickHour) {
            int h = Math.floorMod(tickHour, 24);
            for (int s : sleepHours) {
                if (s == h) return true;
            }
            return false;
        }

        /** Nocturnal = the sleep window falls mostly in daytime work hours (tick 1..11). */
        public boolean isNocturnal() {
            int day = 0;
            for (int s : sleepHours) {
                if (s >= 1 && s <= 11) day++;
            }
            return sleepHours.length > 0 && day * 2 > sleepHours.length;
        }
    }

    public static Resolved resolve(VillagerEntityMCA villager) {
        Map<String, String> carried = TownsteadVillagers.get(villager).life().carriedVariants();
        for (Map.Entry<String, String> e : carried.entrySet()) {
            Gene gene = geneById(e.getKey());
            if (gene != null && isChronotype(gene)) {
                return fromGene(gene, e.getValue());
            }
        }
        // Backfill rolls a carried variant on load, so this only covers the brief window
        // before that runs (or a setup with no chronotype gene loaded).
        return fromCatalog(DEFAULT_VARIANT);
    }

    /** Resolve a carried variant against the gene that granted it (window baked at load). */
    private static Resolved fromGene(Gene gene, String variantId) {
        GeneVariant v = findVariant(gene, variantId);
        if (v == null) v = gene.variants().get(0);
        if (v.instance() instanceof ChronotypeGeneType.Instance ci && ci.sleepHours().length > 0) {
            return new Resolved(v.id(), v.displayName().getString(), ci.sleepHours());
        }
        return fromCatalog(variantId);
    }

    /** Resolve a bare variant id against the shared catalog (the legacy / fallback path). */
    private static Resolved fromCatalog(String variantId) {
        ChronotypeCatalog.Entry entry = ChronotypeCatalog.get(variantId);
        if (entry != null && entry.sleepHours().length > 0) {
            return new Resolved(variantId, entry.label().getString(), entry.sleepHours());
        }
        String id = variantId == null || variantId.isEmpty() ? DEFAULT_VARIANT : variantId;
        return new Resolved(id, id, DEFAULT_SLEEP);
    }

    private static boolean isChronotype(Gene gene) {
        return !gene.variants().isEmpty()
                && gene.variants().get(0).instance() instanceof ChronotypeGeneType.Instance;
    }

    @Nullable
    private static Gene geneById(String geneId) {
        ResourceLocation id = ResourceLocation.tryParse(geneId);
        return id == null ? null : GeneRegistry.byId(id);
    }

    @Nullable
    private static GeneVariant findVariant(Gene gene, String variantId) {
        if (variantId == null || variantId.isEmpty()) return null;
        for (GeneVariant v : gene.variants()) {
            if (v.id().equals(variantId)) return v;
        }
        return null;
    }
}
