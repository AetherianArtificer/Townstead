package com.aetherianartificer.townstead.origin.gene.types;

import com.aetherianartificer.townstead.origin.gene.GeneDisplay;
import com.aetherianartificer.townstead.origin.gene.GeneInstance;
import com.aetherianartificer.townstead.origin.gene.GeneType;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

/**
 * What a race eats (omnivore, herbivore, carnivore, …), or {@code "none"} for a race that does not
 * eat at all (e.g. undead sustained by magic). The {@code "none"} value switches off the hunger
 * need entirely: the hunger bar is pinned full server-side (no decay, so the refuel task never
 * fires) and its interact-screen icon is hidden. Other diets are display-only for now.
 *
 * <p>JSON: {@code { "type":"townstead_origins:diet", "diet":"omnivore" }} (or {@code "diet":"none"}).</p>
 */
public final class DietGeneType implements GeneType {

    public static final String KEY = "townstead_origins:diet";

    /** The diet value that means "does not eat", switching off the hunger need. */
    public static final String NONE = "none";

    public record Instance(String diet) implements GeneInstance {
        @Override public String typeKey() { return KEY; }

        @Override public GeneDisplay display() {
            return disablesHunger()
                    ? GeneDisplay.suppressNeed(java.util.List.of("hunger"))
                    : GeneDisplay.PRESENCE;
        }

        /** True when this race does not eat, switching off the hunger need. */
        public boolean disablesHunger() { return NONE.equalsIgnoreCase(diet); }
    }

    @Override
    public String key() { return KEY; }

    @Override
    public GeneInstance parse(JsonObject json) {
        String diet = GsonHelper.getAsString(json, "diet", "");
        if (diet.isBlank()) return null;
        return new Instance(diet);
        // TODO(effects): gate which foods nourish the villager by this diet.
    }
}
