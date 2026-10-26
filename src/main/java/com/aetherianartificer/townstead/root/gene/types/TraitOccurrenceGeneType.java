package com.aetherianartificer.townstead.root.gene.types;

import com.aetherianartificer.townstead.root.gene.GeneDisplay;
import com.aetherianartificer.townstead.root.gene.GeneInstance;
import com.aetherianartificer.townstead.root.gene.GeneType;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

/**
 * A gene whose power biases an <b>MCA base trait</b>'s manifestation chance
 * (e.g. {@code mca:dwarfism}, {@code albinism}). {@code delta} is added to the
 * trait's natural occurrence; {@code "force": true} is shorthand for delta 1.0
 * (guarantee the trait manifests). Multiple such genes on a villager stack.
 *
 * <p>The {@code trait} is an MCA {@code Traits} id (built-in or a data-pack trait
 * Townstead bridges into MCA's registry). At spawn {@code RootSpawnHandler} grants
 * it via {@code villager.getTraits().addTrait} with this gene's occurrence as the
 * probability ({@code force}/{@code delta} ≥ 1.0 = guaranteed).</p>
 *
 * <p>{@code "block": true} rules the trait out instead (delta -1): it is never granted, and
 * {@code TraitBlocks} removes it wherever MCA would give it (spawn roll, birth, root change).</p>
 *
 * <p>JSON: {@code { "type":"townstead_roots:trait_occurrence", "trait":"dwarfism",
 * "force":true }}, {@code { ..., "trait":"albinism", "delta":0.25 }} or
 * {@code { ..., "trait":"dwarfism", "block":true }}</p>
 */
public final class TraitOccurrenceGeneType implements GeneType {

    @Override
    public com.aetherianartificer.townstead.root.gene.GeneLayer layer() {
        return com.aetherianartificer.townstead.root.gene.GeneLayer.HABITS;
    }

    public static final String KEY = "townstead_roots:trait_occurrence";

    public record Instance(String trait, float delta) implements GeneInstance {
        @Override
        public String typeKey() { return KEY; }

        @Override
        public GeneDisplay display() { return GeneDisplay.influence(trait, delta); }

        /** Whether this gene rules its trait out rather than granting it. */
        public boolean blocks() { return delta <= -1f; }
    }

    @Override
    public String key() { return KEY; }

    @Override
    public GeneInstance parse(JsonObject json) {
        String trait = GsonHelper.getAsString(json, "trait", "");
        if (trait.isBlank()) return null;
        float delta = GsonHelper.getAsBoolean(json, "block", false) ? -1.0f
                : GsonHelper.getAsBoolean(json, "force", false) ? 1.0f
                : GsonHelper.getAsFloat(json, "delta", 0f);
        return new Instance(trait, delta);
        // Townstead traits apply at spawn (RootSpawnHandler); MCA-trait biasing is still TODO.
    }
}
