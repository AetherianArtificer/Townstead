package com.aetherianartificer.townstead.root.gene.types;

import com.aetherianartificer.townstead.root.gene.GeneDisplay;
import com.aetherianartificer.townstead.root.gene.GeneInstance;
import com.aetherianartificer.townstead.root.gene.GeneType;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

/**
 * What this body bleeds. Gore Traces (blood, wounds) land as {@code trace} instead, so a honey-filled
 * body leaks honey and a skeleton sheds bone dust. An empty {@code trace} bleeds nothing.
 *
 * <p>JSON: {@code { "type":"pheno:bleeds", "trace":"examplepack:ichor" }}</p>
 */
public final class BleedsGeneType implements GeneType {

    public static final String KEY = "pheno:bleeds";

    /**
     * {@code trace} replaces the gore Traces when {@code replacesTrace}, and empty then means it bleeds
     * nothing. {@code liquid} is the material, and {@code color} and {@code dryColor} tint it across any
     * color (-1 for the liquid's own).
     */
    public record Instance(String trace, boolean replacesTrace, String liquid, int color, int dryColor) implements GeneInstance {
        @Override public String typeKey() { return KEY; }
        @Override public GeneDisplay display() { return GeneDisplay.PRESENCE; }
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public GeneInstance parse(JsonObject json) {
        return new Instance(GsonHelper.getAsString(json, "trace", ""), json.has("trace"), GsonHelper.getAsString(json, "liquid", ""),
                color(json, "color"), color(json, "dry_color"));
    }

    private static int color(JsonObject json, String key) {
        if (!json.has(key)) return -1;
        String text = json.get(key).getAsString();
        try {
            return Integer.parseInt(text.startsWith("#") ? text.substring(1) : text, 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
