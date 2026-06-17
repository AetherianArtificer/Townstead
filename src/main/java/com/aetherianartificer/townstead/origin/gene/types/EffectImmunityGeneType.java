package com.aetherianartificer.townstead.origin.gene.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.origin.gene.GeneDisplay;
import com.aetherianartificer.townstead.origin.gene.GeneInstance;
import com.aetherianartificer.townstead.origin.gene.GeneType;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Makes a race immune to listed status effects (or all of them). Enforced each tick
 * by removing the effect if present; durational effects (poison, wither, slowness)
 * are cleared within the tick interval. (Instantaneous effects like instant damage
 * aren't blocked, since they resolve immediately.)
 *
 * <p>JSON: {@code { "type":"pheno:effect_immunity",
 * "effects":["minecraft:poison","minecraft:wither"] }} or {@code { ..., "all":true }}</p>
 */
public final class EffectImmunityGeneType implements GeneType {

    public static final String KEY = "pheno:effect_immunity";

    public record Instance(Set<ResourceLocation> effects, boolean all) implements GeneInstance {
        public Instance { effects = Set.copyOf(effects); }
        @Override public String typeKey() { return KEY; }
        @Override public GeneDisplay display() { return GeneDisplay.PRESENCE; }
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public GeneInstance parse(JsonObject json) {
        boolean all = GsonHelper.getAsBoolean(json, "all", false);
        Set<ResourceLocation> effects = new LinkedHashSet<>();
        for (var element : GsonHelper.getAsJsonArray(json, "effects", new JsonArray())) {
            ResourceLocation id = DataPackLang.parseId(element.getAsString());
            if (id != null) effects.add(id);
        }
        if (!all && effects.isEmpty()) return null;
        return new Instance(effects, all);
    }
}
