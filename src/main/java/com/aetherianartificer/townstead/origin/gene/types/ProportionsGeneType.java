package com.aetherianartificer.townstead.origin.gene.types;

import com.aetherianartificer.townstead.origin.GeneRange;
import com.aetherianartificer.townstead.origin.OriginGenes;
import com.aetherianartificer.townstead.origin.gene.GeneDisplay;
import com.aetherianartificer.townstead.origin.gene.GeneInstance;
import com.aetherianartificer.townstead.origin.gene.GeneType;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One gene describing a race's whole build, in two parts:
 *
 * <ul>
 *   <li><b>Body metrics</b> — {@code [min,max]} ranges over MCA's continuous float genes
 *       ({@code size}, {@code width}, …), keyed by their normalized MCA key. Rolled into MCA's
 *       genetics at spawn (so they drive the server hitbox and blend continuously at breeding),
 *       exactly like {@link BodyMetricGeneType} — bundled here so a race's whole build is one gene.</li>
 *   <li><b>Render proportions</b> — a part-name → multiplier map. MCA scales the whole model (every
 *       part) by the build's {@code (horiz, vert)}, which squashes non-uniform builds; each listed
 *       part has that squash neutralized (anchored at the geometric mean, so size is preserved) and is
 *       then scaled by its factor. {@code 1.0} = proportioned, no resize; {@code >1}/{@code <1} =
 *       stylize. An unlisted part keeps MCA's normal squash. This is a client render effect.
 *       <p>Part keys are <b>not</b> validated here — they are free-form and shape-specific. Each
 *       species' render hook owns its own vocabulary and binds the keys it recognizes to actual
 *       {@code ModelPart}s; keys it doesn't know are ignored ({@code GeneCatalogEntry.proportionScale}
 *       returns {@code NaN} → no-op). Humanoid (MCA villager model) reads {@code head}/{@code arms}/
 *       {@code legs}/{@code body} in {@code VillagerHeadProportionMixin}; a future shape would read its
 *       own (e.g. {@code leg_front}/{@code tail}) in its own hook.</li>
 * </ul>
 *
 * <p>JSON: {@code { "type":"pheno:proportions", "category":"Appearance",
 * "size":{"min":0.0,"max":0.2}, "width":{"min":0.5,"max":0.7}, "head":1.0, "arms":1.0, "legs":1.0 }}.
 * All body metrics in the bundle share one auto-locus, so a lineage's proportions gene replaces its
 * ancestry's whole build at once.</p>
 */
public final class ProportionsGeneType implements GeneType {

    public static final String KEY = "pheno:proportions";

    private static final ResourceLocation LOCUS = ResourceLocation.tryParse("pheno:proportions");

    public record Instance(Map<String, GeneRange> bodyMetrics, Map<String, Float> partScales)
            implements GeneInstance {
        @Override public String typeKey() { return KEY; }
        @Override public GeneDisplay display() { return GeneDisplay.proportions(partScales); }
    }

    @Override
    public String key() { return KEY; }

    @Override
    public GeneInstance parse(JsonObject json) {
        Map<String, GeneRange> metrics = new LinkedHashMap<>();
        Map<String, Float> parts = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            String raw = entry.getKey();
            JsonElement value = entry.getValue();
            String target = OriginGenes.normalizeKey(raw);
            if (value.isJsonObject() && OriginGenes.isKnown(target)) {
                JsonObject range = value.getAsJsonObject();
                float min = GsonHelper.getAsFloat(range, "min", 0f);
                float max = GsonHelper.getAsFloat(range, "max", 1f);
                metrics.put(target, new GeneRange(min, max));
            } else if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
                // Free-form, shape-specific part key (see class doc) — not validated against a vocabulary.
                parts.put(raw, value.getAsFloat());
            }
        }
        if (metrics.isEmpty() && parts.isEmpty()) return null;
        return new Instance(Map.copyOf(metrics), Map.copyOf(parts));
    }

    @Override
    @Nullable
    public ResourceLocation defaultLocus(GeneInstance instance) {
        return LOCUS;
    }
}
