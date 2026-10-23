package com.aetherianartificer.townstead.root.gene.types;

import com.aetherianartificer.townstead.root.gene.GeneDisplay;
import com.aetherianartificer.townstead.root.gene.GeneInstance;
import com.aetherianartificer.townstead.root.gene.GeneType;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

/**
 * What liquid a race hydrates on (water, milk, blood, …), or {@code "none"} for a race that does not
 * drink at all (e.g. undead sustained by magic). The {@code "none"} value switches off the thirst
 * need entirely: thirst is pinned full server-side (no decay, so the refuel task never fires) and its
 * interact-screen icon is hidden. Other liquids are display-only for now. {@code rate} is the share
 * of normal thirst the body feels, from 0 to 1; 0 is the same as {@code "none"}. A gene may set
 * {@code rate} alone, on a locus of its own, to slow a carrier's thirst without naming a liquid.
 *
 * <p>JSON: {@code { "type":"townstead_roots:hydration", "liquid":"water" }} (or {@code "liquid":"none"},
 * or {@code "rate":0.5}).</p>
 */
public final class HydrationGeneType implements GeneType {

    @Override
    public com.aetherianartificer.townstead.root.gene.GeneLayer layer() {
        return com.aetherianartificer.townstead.root.gene.GeneLayer.HABITS;
    }

    public static final String KEY = "townstead_roots:hydration";

    /** The liquid value that means "does not drink", switching off the thirst need. */
    public static final String NONE = "none";

    // One hydration source per creature: matches the locus every pack already declares.
    private static final net.minecraft.resources.ResourceLocation LOCUS =
            com.aetherianartificer.townstead.data.DataPackLang.parseId(KEY);

    public record Instance(String liquid, float rate) implements GeneInstance {
        public Instance(String liquid) {
            this(liquid, 1f);
        }

        @Override public String typeKey() { return KEY; }

        @Override public GeneDisplay display() {
            return disablesThirst()
                    ? GeneDisplay.suppressNeed(java.util.List.of("thirst"))
                    : GeneDisplay.PRESENCE;
        }

        /** True when this race does not drink, switching off the thirst need. */
        public boolean disablesThirst() { return NONE.equalsIgnoreCase(liquid) || rate <= 0f; }
    }

    @Override
    public String key() { return KEY; }

    @Override
    public GeneInstance parse(JsonObject json) {
        String liquid = GsonHelper.getAsString(json, "liquid", "");
        if (liquid.isBlank() && !json.has("rate")) return null;
        return new Instance(liquid, Math.max(0f, Math.min(1f, GsonHelper.getAsFloat(json, "rate", 1f))));
        // TODO(effects): gate which liquids quench thirst by this value.
    }

    @Override
    public net.minecraft.resources.ResourceLocation defaultLocus(GeneInstance instance) {
        return LOCUS;
    }
}
