package com.aetherianartificer.townstead.origin.gene.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.aetherianartificer.townstead.origin.gene.GeneDisplay;
import com.aetherianartificer.townstead.origin.gene.GeneInstance;
import com.aetherianartificer.townstead.origin.gene.GeneType;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Applies effects whose strength ramps the longer a {@code condition} holds and decays
 * when it doesn't (Origins' {@code stacking_status_effect}, e.g. Elytrian claustrophobia
 * weakening you the longer you're boxed in). A per-entity stack counter climbs to
 * {@code max_stacks} while the condition is met and falls to {@code min_stacks} otherwise;
 * the effect amplifier is {@code stacks / stacks_per_level}. Ticked server-side.
 *
 * <p>JSON: {@code { "type":"pheno:stacking_effect", "max_stacks":360,
 * "stacks_per_level":60, "effects":[{"effect":"minecraft:slowness"}],
 * "condition":{ "type":"pheno:block_at", "offset_y":1, ... } }}</p>
 */
public final class StackingEffectGeneType implements GeneType {

    public static final String KEY = "pheno:stacking_effect";

    public record EffectSpec(ResourceLocation effect, boolean ambient, boolean particles, boolean icon) {}

    public record Instance(@Nullable Condition condition, List<EffectSpec> effects,
                           int minStacks, int maxStacks, int stacksPerLevel) implements GeneInstance {
        @Override public String typeKey() { return KEY; }
        @Override public GeneDisplay display() { return GeneDisplay.PRESENCE; }
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public GeneInstance parse(JsonObject json) {
        List<EffectSpec> effects = new ArrayList<>();
        if (json.has("effects") && json.get("effects").isJsonArray()) {
            for (var el : json.getAsJsonArray("effects")) {
                if (!el.isJsonObject()) continue;
                JsonObject obj = el.getAsJsonObject();
                ResourceLocation id = DataPackLang.parseId(GsonHelper.getAsString(obj, "effect", ""));
                if (id == null) continue;
                effects.add(new EffectSpec(id,
                        GsonHelper.getAsBoolean(obj, "is_ambient", false),
                        GsonHelper.getAsBoolean(obj, "show_particles", true),
                        GsonHelper.getAsBoolean(obj, "show_icon", true)));
            }
        }
        if (json.has("effect")) {
            ResourceLocation id = DataPackLang.parseId(GsonHelper.getAsString(json, "effect", ""));
            if (id != null) effects.add(new EffectSpec(id, false, true, true));
        }
        if (effects.isEmpty()) return null;
        Condition condition = json.has("condition") ? Conditions.parse(json.get("condition")) : null;
        int minStacks = GsonHelper.getAsInt(json, "min_stacks", 0);
        int maxStacks = Math.max(1, GsonHelper.getAsInt(json, "max_stacks", 60));
        int stacksPerLevel = Math.max(1, GsonHelper.getAsInt(json, "stacks_per_level", 60));
        return new Instance(condition, effects, minStacks, maxStacks, stacksPerLevel);
    }
}
