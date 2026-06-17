package com.aetherianartificer.townstead.origin.gene.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.origin.gene.GeneDisplay;
import com.aetherianartificer.townstead.origin.gene.GeneInstance;
import com.aetherianartificer.townstead.origin.gene.GeneType;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

/**
 * Whether (and how readily) the bearer can reproduce. {@code fertility} is a {@code [0,1]} value:
 * {@code 0} means sterile (never breeds), {@code 1} (default) is normal. Enforced server-side by
 * {@code Fertility}, which blocks MCA's procreation/gestation when a prospective parent is sterile.
 * The in-between range is reserved for later (scaling breeding chance / cooldown).
 *
 * <p>JSON: {@code { "type":"townstead_origins:fertility", "fertility":0.0 }}</p>
 */
public final class FertilityGeneType implements GeneType {

    public static final String KEY = "townstead_origins:fertility";

    // All fertility genes share one locus, so they inherit as alleles of one another rather than
    // stacking, the same way body-metric genes targeting one MCA stat collapse.
    private static final ResourceLocation LOCUS = DataPackLang.parseId(KEY);

    public record Instance(float fertility) implements GeneInstance {
        @Override public String typeKey() { return KEY; }
        @Override public GeneDisplay display() { return GeneDisplay.range(0f, 1f); }

        /** True when this gene leaves the bearer able to reproduce at all. */
        public boolean fertile() { return fertility > 0f; }
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public GeneInstance parse(JsonObject json) {
        float fertility = GsonHelper.getAsFloat(json, "fertility", 1.0f);
        return new Instance(Math.max(0f, Math.min(1f, fertility)));
    }

    @Override
    public ResourceLocation defaultLocus(GeneInstance instance) {
        return LOCUS;
    }
}
