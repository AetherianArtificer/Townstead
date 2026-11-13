package com.aetherianartificer.townstead.root.gene.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.root.gene.GeneDisplay;
import com.aetherianartificer.townstead.root.gene.GeneInstance;
import com.aetherianartificer.townstead.root.gene.GeneType;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import java.util.HashMap;
import java.util.Map;

/**
 * Turns what the body can't use into a meter. The bearer can keep eating past full, and food eaten when
 * already full fills {@code resource} by {@code per_nutrition} for each point of nutrition. {@code items}
 * name things that fill it by a set amount whenever they're consumed, hungry or not.
 *
 * <p>JSON: {@code { "type":"pheno:overfeed", "resource":"examplepack:charge", "per_nutrition":2,
 * "items":{ "minecraft:honey_bottle":30 } }}</p>
 */
public final class OverfeedGeneType implements GeneType {

    public static final String KEY = "pheno:overfeed";

    public record Instance(ResourceLocation resource, int perNutrition, Map<ResourceLocation, Integer> items)
            implements GeneInstance {
        public Instance { items = Map.copyOf(items); }
        @Override public String typeKey() { return KEY; }
        @Override public GeneDisplay display() { return GeneDisplay.PRESENCE; }
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public GeneInstance parse(JsonObject json) {
        ResourceLocation resource = DataPackLang.parseId(GsonHelper.getAsString(json, "resource", ""));
        if (resource == null) return null;
        Map<ResourceLocation, Integer> items = new HashMap<>();
        if (json.has("items") && json.get("items").isJsonObject()) {
            for (var entry : json.getAsJsonObject("items").entrySet()) {
                ResourceLocation id = DataPackLang.parseId(entry.getKey());
                if (id != null) items.put(id, entry.getValue().getAsInt());
            }
        }
        return new Instance(resource, GsonHelper.getAsInt(json, "per_nutrition", 1), items);
    }
}
