package com.aetherianartificer.townstead.root.gene.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.root.gene.GeneDisplay;
import com.aetherianartificer.townstead.root.gene.GeneInstance;
import com.aetherianartificer.townstead.root.gene.GeneType;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

/**
 * Whether the carrier can carry a child. Unlike fertility, it does not stop them fathering one: a
 * vampire man can have a child with a living woman, but a vampire woman cannot bear one. Its own
 * locus, so granting it never replaces the carrier's fertility.
 *
 * <p>JSON: {@code { "type":"townstead_roots:bearing", "bearing":false }}</p>
 */
public final class BearingGeneType implements GeneType {

    public static final String KEY = "townstead_roots:bearing";
    private static final ResourceLocation LOCUS = DataPackLang.parseId(KEY);

    public record Instance(boolean bearing) implements GeneInstance {
        @Override public String typeKey() { return KEY; }
        @Override public GeneDisplay display() { return GeneDisplay.PRESENCE; }
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public GeneInstance parse(JsonObject json) {
        return new Instance(GsonHelper.getAsBoolean(json, "bearing", true));
    }

    @Override
    public ResourceLocation defaultLocus(GeneInstance instance) {
        return LOCUS;
    }
}
