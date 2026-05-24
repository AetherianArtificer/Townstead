package com.aetherianartificer.townstead.origin.gene.types;

import com.aetherianartificer.townstead.origin.gene.GeneDisplay;
import com.aetherianartificer.townstead.origin.gene.GeneInstance;
import com.aetherianartificer.townstead.origin.gene.GeneType;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

/**
 * A gene whose power biases an <b>MCA base trait</b>'s manifestation chance
 * (e.g. {@code mca:dwarfism}, {@code albinism}). {@code delta} is added to the
 * trait's natural occurrence; {@code "force": true} is shorthand for delta 1.0
 * (guarantee the trait manifests). Multiple such genes on a villager stack.
 *
 * <p>The {@code trait} is an MCA {@code Traits} registry key (kept as a plain
 * string here — resolved against MCA at runtime, which is deferred; this
 * iteration is config + display only).</p>
 *
 * <p>JSON: {@code { "type":"townstead_origins:trait_occurrence", "trait":"dwarfism",
 * "force":true }} or {@code { ..., "trait":"albinism", "delta":0.25 }}</p>
 */
public final class TraitOccurrenceGeneType implements GeneType {

    public static final String KEY = "townstead_origins:trait_occurrence";

    public record Instance(String trait, float delta) implements GeneInstance {
        @Override
        public String typeKey() { return KEY; }

        @Override
        public GeneDisplay display() { return GeneDisplay.influence(trait, delta); }
    }

    @Override
    public String key() { return KEY; }

    @Override
    public GeneInstance parse(JsonObject json) {
        String trait = GsonHelper.getAsString(json, "trait", "");
        if (trait.isBlank()) return null;
        float delta = GsonHelper.getAsBoolean(json, "force", false)
                ? 1.0f
                : GsonHelper.getAsFloat(json, "delta", 0f);
        return new Instance(trait, delta);
        // TODO(runtime): bias MCA Traits.randomize() for `trait` by `delta` (clamped); 1.0 = force.
    }
}
