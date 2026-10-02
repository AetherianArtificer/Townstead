package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;

import java.util.ArrayList;
import java.util.List;

/**
 * True when a block or item is in this game, from whatever mod adds it. A {@code #} id is a tag,
 * true when anything is in it. With a list, when any of them is. Use it to offer content only
 * where its pieces exist, without naming the mod.
 * <pre>{ "type": "pheno:exists", "block": "farmersdelight:rich_soil_farmland" }</pre>
 * <pre>{ "type": "pheno:exists", "item": ["#c:fertilizers", "minecraft:bone_meal"] }</pre>
 */
public final class ExistsConditionType implements ConditionType {
    public static final String KEY = "pheno:exists";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        List<String> blocks = ids(json.get("block"));
        List<String> items = ids(json.get("item"));
        if (blocks == null || items == null || blocks.isEmpty() && items.isEmpty()) return null;
        return new Condition() {
            // Tags fill in when data loads, so this reads them at test time.
            @Override public boolean test(ConditionContext ctx) {
                return blocks.stream().anyMatch(id -> exists(BuiltInRegistries.BLOCK, Registries.BLOCK, id))
                        || items.stream().anyMatch(id -> exists(BuiltInRegistries.ITEM, Registries.ITEM, id));
            }
            @Override public boolean supportsSubject() { return true; }
        };
    }

    /** The ids in a string or list, an empty list when absent, or null when one is not an id. */
    private static List<String> ids(JsonElement raw) {
        List<String> out = new ArrayList<>();
        if (raw == null) return out;
        if (raw.isJsonArray()) raw.getAsJsonArray().forEach(e -> out.add(e.getAsString().trim()));
        else out.add(raw.getAsString().trim());
        for (String id : out) {
            if (ResourceLocation.tryParse(id.startsWith("#") ? id.substring(1) : id) == null) return null;
        }
        return out;
    }

    private static <T> boolean exists(Registry<T> registry, ResourceKey<? extends Registry<T>> key, String raw) {
        boolean tag = raw.startsWith("#");
        ResourceLocation id = ResourceLocation.tryParse(tag ? raw.substring(1) : raw);
        if (id == null) return false;
        if (!tag) return registry.containsKey(id);
        return registry.getTag(TagKey.create(key, id)).map(set -> set.size() > 0).orElse(false);
    }
}
