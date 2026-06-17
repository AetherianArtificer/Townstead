package com.aetherianartificer.townstead.origin.gene.types;

import com.aetherianartificer.townstead.origin.GeneRange;
import com.aetherianartificer.townstead.origin.OriginGenes;
import com.aetherianartificer.townstead.origin.gene.GeneDisplay;
import com.aetherianartificer.townstead.origin.gene.GeneInstance;
import com.aetherianartificer.townstead.origin.gene.GeneType;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

/**
 * Constrains one of MCA's continuous float genes (size, width, melanin, …) to a
 * {@code [min,max]} range. This is how MCA's body floats become first-class,
 * authorable genes in the unified {@code genes} array: a race lists a body-metric
 * gene like any other, and the range seeds the founder roll and re-clamps the
 * blended value on a bred villager. The value itself lives on MCA's genetics
 * (continuous, blended at breeding), not in the diploid {@link com.aetherianartificer.townstead.origin.gene.Genotype}.
 *
 * <p>JSON: {@code { "type":"pheno:body_metric", "target":"size",
 * "min":0.55, "max":0.75 }}. Genes targeting the same MCA float share an
 * auto-derived locus, so a lineage's size gene replaces its ancestry's.</p>
 */
public final class BodyMetricGeneType implements GeneType {

    public static final String KEY = "pheno:body_metric";

    /** Auto-locus namespace for body metrics (one locus per MCA float target). */
    private static final String LOCUS_NS = "townstead_origins";

    /** {@code target} is the normalized MCA gene key (see {@link OriginGenes#normalizeKey}). */
    public record Instance(String target, GeneRange range) implements GeneInstance {
        @Override public String typeKey() { return KEY; }
        @Override public GeneDisplay display() { return GeneDisplay.range(range.min(), range.max()); }
    }

    @Override
    public String key() { return KEY; }

    @Override
    public GeneInstance parse(JsonObject json) {
        String target = OriginGenes.normalizeKey(GsonHelper.getAsString(json, "target", ""));
        if (target.isEmpty() || !OriginGenes.isKnown(target)) return null;
        float min = GsonHelper.getAsFloat(json, "min", 0f);
        float max = GsonHelper.getAsFloat(json, "max", 1f);
        return new Instance(target, new GeneRange(min, max));
    }

    @Override
    @Nullable
    public ResourceLocation defaultLocus(GeneInstance instance) {
        if (instance instanceof Instance metric) {
            return ResourceLocation.tryParse(LOCUS_NS + ":body/" + metric.target());
        }
        return null;
    }
}
