package com.aetherianartificer.townstead.root.gene.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.root.gene.GeneDisplay;
import com.aetherianartificer.townstead.root.gene.GeneInstance;
import com.aetherianartificer.townstead.root.gene.GeneType;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

/**
 * How many offspring the bearer produces per birth (the clutch / litter size). {@code 1} (default) is
 * MCA's normal single child; an egg-laying race (spiders) sets it higher to lay several eggs at once.
 * Read server-side on the MOTHER at the pregnancy birth ({@code LitterSize}/{@code LitterGestationMixin}),
 * which spawns {@code litter_size - 1} extra children beyond MCA's one.
 *
 * <p>JSON: {@code { "type":"townstead_roots:litter_size", "litter_size":4 }}</p>
 */
public final class LitterSizeGeneType implements GeneType {

    @Override
    public com.aetherianartificer.townstead.root.gene.GeneLayer layer() {
        return com.aetherianartificer.townstead.root.gene.GeneLayer.BEING;
    }

    public static final String KEY = "townstead_roots:litter_size";

    private static final ResourceLocation LOCUS = DataPackLang.parseId(KEY);

    public record Instance(int litterSize) implements GeneInstance {
        @Override public String typeKey() { return KEY; }
        @Override public GeneDisplay display() { return GeneDisplay.range(1f, 12f); }
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public GeneInstance parse(JsonObject json) {
        int n = GsonHelper.getAsInt(json, "litter_size", 1);
        return new Instance(Math.max(1, Math.min(12, n)));
    }

    @Override
    public ResourceLocation defaultLocus(GeneInstance instance) {
        return LOCUS;
    }
}
