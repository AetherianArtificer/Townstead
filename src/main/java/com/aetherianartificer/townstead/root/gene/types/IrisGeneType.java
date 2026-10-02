package com.aetherianartificer.townstead.root.gene.types;

import com.aetherianartificer.townstead.root.gene.GeneDisplay;
import com.aetherianartificer.townstead.root.gene.GeneInstance;
import com.aetherianartificer.townstead.root.gene.GeneType;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

/**
 * Recolors the iris of MCA's own eyes, whatever face the bearer has, keeping its shape, lashes and
 * blink. {@code glow} also draws the iris emissive. Only MCA's humanoid face has these eye layers;
 * a custom rig ignores the gene.
 *
 * <p>JSON: {@code { "type":"townstead_roots:iris", "tint":"#D0151F", "glow":true }}</p>
 */
public final class IrisGeneType implements GeneType {

    public static final String KEY = "townstead_roots:iris";

    public record Instance(int tint, boolean glow) implements GeneInstance {
        @Override public String typeKey() { return KEY; }
        @Override public GeneDisplay display() { return GeneDisplay.color(tint, 0, 1f); }
    }

    // One iris per face: a later iris grant (a glowing elder's) replaces an earlier one.
    private static final net.minecraft.resources.ResourceLocation LOCUS =
            net.minecraft.resources.ResourceLocation.tryParse(KEY);

    @Override
    public String key() { return KEY; }

    @Override
    public net.minecraft.resources.ResourceLocation defaultLocus(GeneInstance instance) {
        return LOCUS;
    }

    @Override
    public GeneInstance parse(JsonObject json) {
        String raw = GsonHelper.getAsString(json, "tint", "").trim();
        if (raw.startsWith("#")) raw = raw.substring(1);
        int tint;
        try {
            tint = Integer.parseInt(raw, 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            return null;
        }
        return new Instance(tint, GsonHelper.getAsBoolean(json, "glow", false));
    }
}
