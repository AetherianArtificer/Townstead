package com.aetherianartificer.townstead.root.gene.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.root.gene.GeneDisplay;
import com.aetherianartificer.townstead.root.gene.GeneInstance;
import com.aetherianartificer.townstead.root.gene.GeneType;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

/**
 * Pregnancy-length multiplier on the MOTHER. {@code 1.0} (default) is MCA's normal duration; {@code 2.0}
 * is twice as long, {@code 0.5} half. Read server-side ({@code GestationLength}/{@code LitterGestationMixin}),
 * which scales MCA's per-tick {@code babyAge} growth by {@code 1 / multiplier} (slower growth = longer
 * pregnancy).
 *
 * <p>JSON: {@code { "type":"townstead_roots:gestation_length", "gestation_length":0.5 }}</p>
 */
public final class GestationLengthGeneType implements GeneType {

    @Override
    public com.aetherianartificer.townstead.root.gene.GeneLayer layer() {
        return com.aetherianartificer.townstead.root.gene.GeneLayer.BEING;
    }

    public static final String KEY = "townstead_roots:gestation_length";

    private static final ResourceLocation LOCUS = DataPackLang.parseId(KEY);

    public record Instance(float gestationLength) implements GeneInstance {
        @Override public String typeKey() { return KEY; }
        @Override public GeneDisplay display() { return GeneDisplay.range(0.25f, 4f); }
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public GeneInstance parse(JsonObject json) {
        float m = GsonHelper.getAsFloat(json, "gestation_length", 1.0f);
        return new Instance(Math.max(0.1f, Math.min(8f, m)));
    }

    @Override
    public ResourceLocation defaultLocus(GeneInstance instance) {
        return LOCUS;
    }
}
