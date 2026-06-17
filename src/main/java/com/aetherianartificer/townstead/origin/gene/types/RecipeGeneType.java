package com.aetherianartificer.townstead.origin.gene.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.origin.gene.GeneDisplay;
import com.aetherianartificer.townstead.origin.gene.GeneInstance;
import com.aetherianartificer.townstead.origin.gene.GeneType;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;

/**
 * Lets only the bearer craft a recipe whose output is {@code result} (Origins' {@code recipe},
 * e.g. Arachnid web crafting). The recipe itself is a normal datapack recipe so it matches;
 * this gene gates *taking* the result — anyone without it sees the result but can't pick it
 * up. Output-item gated (so a non-gated recipe for the same item is gated too — keep the
 * output distinct).
 *
 * <p>JSON: {@code { "type":"pheno:recipe", "result":"minecraft:cobweb" }}</p>
 */
public final class RecipeGeneType implements GeneType {

    public static final String KEY = "pheno:recipe";

    public record Instance(Item result) implements GeneInstance {
        @Override public String typeKey() { return KEY; }
        @Override public GeneDisplay display() { return GeneDisplay.PRESENCE; }
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public GeneInstance parse(JsonObject json) {
        ResourceLocation id = DataPackLang.parseId(GsonHelper.getAsString(json, "result", ""));
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return null;
        return new Instance(BuiltInRegistries.ITEM.get(id));
    }
}
