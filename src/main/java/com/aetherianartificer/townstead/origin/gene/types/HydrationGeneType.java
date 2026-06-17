package com.aetherianartificer.townstead.origin.gene.types;

import com.aetherianartificer.townstead.origin.gene.GeneDisplay;
import com.aetherianartificer.townstead.origin.gene.GeneInstance;
import com.aetherianartificer.townstead.origin.gene.GeneType;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

/**
 * What liquid a race hydrates on (water, milk, blood, …), or {@code "none"} for a race that does not
 * drink at all (e.g. undead sustained by magic). The {@code "none"} value switches off the thirst
 * need entirely: thirst is pinned full server-side (no decay, so the refuel task never fires) and its
 * interact-screen icon is hidden. Other liquids are display-only for now.
 *
 * <p>JSON: {@code { "type":"townstead_origins:hydration", "liquid":"water" }} (or {@code "liquid":"none"}).</p>
 */
public final class HydrationGeneType implements GeneType {

    public static final String KEY = "townstead_origins:hydration";

    /** The liquid value that means "does not drink", switching off the thirst need. */
    public static final String NONE = "none";

    public record Instance(String liquid) implements GeneInstance {
        @Override public String typeKey() { return KEY; }

        @Override public GeneDisplay display() {
            return disablesThirst()
                    ? GeneDisplay.suppressNeed(java.util.List.of("thirst"))
                    : GeneDisplay.PRESENCE;
        }

        /** True when this race does not drink, switching off the thirst need. */
        public boolean disablesThirst() { return NONE.equalsIgnoreCase(liquid); }
    }

    @Override
    public String key() { return KEY; }

    @Override
    public GeneInstance parse(JsonObject json) {
        String liquid = GsonHelper.getAsString(json, "liquid", "");
        if (liquid.isBlank()) return null;
        return new Instance(liquid);
        // TODO(effects): gate which liquids quench thirst by this value.
    }
}
