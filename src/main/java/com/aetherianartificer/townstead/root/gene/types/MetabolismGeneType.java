package com.aetherianartificer.townstead.root.gene.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.root.gene.GeneDisplay;
import com.aetherianartificer.townstead.root.gene.GeneInstance;
import com.aetherianartificer.townstead.root.gene.GeneType;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

/**
 * Whether the body makes its own warmth. An {@code endotherm} (the default) holds its neutral and
 * warms with work; an {@code ectotherm} follows the air, gains nothing from activity, and basks:
 * its cold relief accepts any sunlit spot, not only a hearth.
 *
 * <p>JSON: {@code { "type":"pheno:metabolism", "metabolism":"ectotherm" }}.</p>
 */
public final class MetabolismGeneType implements GeneType {

    public static final String KEY = "pheno:metabolism";
    public static final String ECTOTHERM = "ectotherm";
    public static final String ENDOTHERM = "endotherm";

    private static final ResourceLocation LOCUS = DataPackLang.parseId(KEY);

    public record Instance(boolean ectotherm) implements GeneInstance {
        @Override public String typeKey() { return KEY; }
        @Override public GeneDisplay display() { return GeneDisplay.PRESENCE; }
    }

    @Override
    public String key() { return KEY; }

    @Override
    public GeneInstance parse(JsonObject json) {
        String metabolism = GsonHelper.getAsString(json, "metabolism", ENDOTHERM);
        return new Instance(ECTOTHERM.equalsIgnoreCase(metabolism));
    }

    @Override
    public ResourceLocation defaultLocus(GeneInstance instance) {
        return LOCUS;
    }
}
