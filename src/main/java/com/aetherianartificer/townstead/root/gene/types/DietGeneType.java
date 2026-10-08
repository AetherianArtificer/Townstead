package com.aetherianartificer.townstead.root.gene.types;

import com.aetherianartificer.townstead.root.gene.GeneDisplay;
import com.aetherianartificer.townstead.root.gene.GeneInstance;
import com.aetherianartificer.townstead.root.gene.GeneType;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

/**
 * What a race eats: the id of a {@code townstead:diet/v1} file ({@code "townstead:lithovore"}), a
 * bare built-in name ({@code "omnivore"}, {@code "carnivore"}, {@code "herbivore"}), or
 * {@code "none"} for a race that does not eat at all (e.g. undead sustained by magic). The
 * {@code "none"} value switches off the hunger need entirely: the hunger bar is pinned full
 * server-side (no decay, so the refuel task never fires) and its interact-screen icon is hidden.
 * A diet naming no loaded file leaves eating as vanilla.
 *
 * <p>JSON: {@code { "type":"townstead_roots:diet", "diet":"omnivore" }} (or {@code "diet":"none"}).</p>
 */
public final class DietGeneType implements GeneType {

    public static final String KEY = "townstead_roots:diet";

    /** The diet value that means "does not eat", switching off the hunger need. */
    public static final String NONE = "none";

    // One diet per creature: matches the locus every pack already declares.
    private static final net.minecraft.resources.ResourceLocation LOCUS =
            com.aetherianartificer.townstead.data.DataPackLang.parseId(KEY);

    public record Instance(String diet, @org.jetbrains.annotations.Nullable String needIcon) implements GeneInstance {
        public Instance(String diet) {
            this(diet, null);
        }

        @Override public String typeKey() { return KEY; }

        @Override public GeneDisplay display() {
            if (disablesHunger()) return GeneDisplay.suppressNeed(java.util.List.of("hunger"));
            return needIcon != null ? GeneDisplay.needIcon("hunger", needIcon) : GeneDisplay.PRESENCE;
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
        String icon = GsonHelper.getAsString(json, "need_icon", "").trim();
        return new Instance(diet, icon.isEmpty() ? null : icon);
    }

    @Override
    public net.minecraft.resources.ResourceLocation defaultLocus(GeneInstance instance) {
        return LOCUS;
    }
}
