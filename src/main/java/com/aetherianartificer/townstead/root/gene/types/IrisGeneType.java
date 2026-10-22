package com.aetherianartificer.townstead.root.gene.types;

import com.aetherianartificer.townstead.root.gene.GeneDisplay;
import com.aetherianartificer.townstead.root.gene.GeneInstance;
import com.aetherianartificer.townstead.root.gene.GeneType;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

/**
 * Recolors the iris of MCA's own eyes, whatever face the bearer has, keeping its shape, lashes and
 * blink. {@code glow} also draws the iris emissive. {@code tint_right} gives the right eye its own
 * colour (mismatched eyes); without it both eyes share {@code tint}. As a variants gene, each variant
 * is one colouring, rolled and inherited like any allele. Only MCA's humanoid face has these eye
 * layers; a custom rig ignores the gene.
 *
 * <p>{@code editable} makes the gene a people's starting eye colour rather than an override: it
 * writes MCA's own eye colour when the villager is founded, changes root, or is born (both eyes, or
 * each its own for {@code tint_right}), and the editor's Eyes tab changes it from there. Only a
 * glowing variant still draws, lighting whatever colour the eyes have. Without it, the gene
 * overrides the eyes outright while it is expressed, which is what a lasting state wants (a
 * vampire's red).</p>
 *
 * <p>JSON: {@code { "type":"townstead_roots:iris", "tint":"#D0151F", "glow":true }}, or
 * {@code { "type":"townstead_roots:iris", "variants": { "odd": { "tint":"#3A8AE0",
 * "tint_right":"#E0A030", "weight":1 } } }}</p>
 */
public final class IrisGeneType implements GeneType {

    public static final String KEY = "townstead_roots:iris";

    /** {@code tintRight} is the right eye's colour, or -1 when both eyes share {@code tint}. */
    public record Instance(int tint, boolean glow, int tintRight, boolean editable) implements GeneInstance {
        public Instance(int tint, boolean glow) {
            this(tint, glow, -1, false);
        }
        public Instance(int tint, boolean glow, int tintRight) {
            this(tint, glow, tintRight, false);
        }
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
        int tint = hex(GsonHelper.getAsString(json, "tint", ""));
        if (tint < 0) return null;
        int right = json.has("tint_right") ? hex(GsonHelper.getAsString(json, "tint_right", "")) : -1;
        return new Instance(tint, GsonHelper.getAsBoolean(json, "glow", false), right,
                GsonHelper.getAsBoolean(json, "editable", false));
    }

    private static int hex(String raw) {
        raw = raw.trim();
        if (raw.startsWith("#")) raw = raw.substring(1);
        try {
            return Integer.parseInt(raw, 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
