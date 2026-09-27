package com.aetherianartificer.townstead.hunger.diet;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.data.ModGate;
import com.aetherianartificer.townstead.data.TownsteadSchema;
import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.Actions;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * What a mouth accepts as food and what each food gives. A diet gene names one of these; an
 * eater without one keeps vanilla behavior (every native food, at its own values).
 *
 * <p>{@code players} also refuses native foods outside the diet for a player of that Root.
 * Villagers always follow their diet.</p>
 */
public record Diet(ResourceLocation id, List<Food> foods, List<ResourceLocation> includes, boolean players) {
    public static final String SCHEMA = "townstead:diet/v1";
    /** Selector for every item carrying native food values. */
    public static final String ANY_FOOD = "*";

    public Diet {
        foods = List.copyOf(foods);
        includes = List.copyOf(includes);
    }

    /**
     * One accepted group. A null {@code nutrition} means native: the item's own food values,
     * and only items that have them.
     */
    public record Food(boolean anyFood, Set<ResourceLocation> items, Set<ResourceLocation> tags,
                       Set<ResourceLocation> excludeItems, Set<ResourceLocation> excludeTags,
                       @Nullable Integer nutrition, float saturation,
                       @Nullable ResourceLocation remainder, @Nullable Action effects) {
        public Food {
            items = Set.copyOf(items);
            tags = Set.copyOf(tags);
            excludeItems = Set.copyOf(excludeItems);
            excludeTags = Set.copyOf(excludeTags);
        }

        public boolean nativeValues() {
            return nutrition == null;
        }

        /** Registry-free match: {@code inTag} answers tag membership, {@code isNativeFood} edibility. */
        public boolean matches(ResourceLocation item, Predicate<ResourceLocation> inTag, boolean isNativeFood) {
            if (excludeItems.contains(item)) return false;
            for (ResourceLocation tag : excludeTags) if (inTag.test(tag)) return false;
            if (nativeValues() && !isNativeFood) return false;
            if (anyFood && isNativeFood) return true;
            if (items.contains(item)) return true;
            for (ResourceLocation tag : tags) if (inTag.test(tag)) return true;
            return false;
        }
    }

    static Diet parse(ResourceLocation id, JsonObject json) {
        TownsteadSchema.validateRequired(json, SCHEMA);
        List<Food> foods = new ArrayList<>();
        if (json.has("foods")) {
            if (!json.get("foods").isJsonArray()) throw new IllegalArgumentException("'foods' must be an array");
            for (JsonElement element : json.getAsJsonArray("foods")) {
                if (!element.isJsonObject()) throw new IllegalArgumentException("each food must be an object");
                JsonObject food = element.getAsJsonObject();
                if (food.has("mods")) {
                    Boolean enabled = ModGate.evaluate(food.get("mods"));
                    if (enabled == null) throw new IllegalArgumentException("a food's 'mods' is malformed");
                    if (!enabled) continue;
                }
                foods.add(food(food));
            }
        }
        List<ResourceLocation> includes = new ArrayList<>();
        if (json.has("includes")) {
            if (!json.get("includes").isJsonArray()) throw new IllegalArgumentException("'includes' must be an array");
            for (JsonElement element : json.getAsJsonArray("includes")) includes.add(id(element.getAsString()));
        }
        if (foods.isEmpty() && includes.isEmpty() && !json.has("foods")) {
            throw new IllegalArgumentException("a diet needs 'foods' or 'includes'");
        }
        return new Diet(id, foods, includes, GsonHelper.getAsBoolean(json, "players", false));
    }

    private static Food food(JsonObject json) {
        Set<ResourceLocation> items = new LinkedHashSet<>();
        Set<ResourceLocation> tags = new LinkedHashSet<>();
        boolean anyFood = selectors(json, "items", items, tags, true);
        if (!anyFood && items.isEmpty() && tags.isEmpty()) throw new IllegalArgumentException("a food needs 'items'");
        Set<ResourceLocation> excludeItems = new LinkedHashSet<>();
        Set<ResourceLocation> excludeTags = new LinkedHashSet<>();
        selectors(json, "exclude", excludeItems, excludeTags, false);

        Integer nutrition = null;
        JsonElement raw = json.get("nutrition");
        if (raw == null || raw.isJsonPrimitive() && raw.getAsJsonPrimitive().isString()
                && "native".equals(raw.getAsString())) {
            nutrition = null;
        } else if (raw.isJsonPrimitive() && raw.getAsJsonPrimitive().isNumber()) {
            nutrition = raw.getAsInt();
            if (nutrition < 0) throw new IllegalArgumentException("'nutrition' cannot be negative");
        } else {
            throw new IllegalArgumentException("'nutrition' must be a number or \"native\"");
        }
        float saturation = GsonHelper.getAsFloat(json, "saturation", 0.3f);
        if (!Float.isFinite(saturation) || saturation < 0) throw new IllegalArgumentException("'saturation' must be a non-negative number");
        ResourceLocation remainder = json.has("remainder") ? id(GsonHelper.getAsString(json, "remainder")) : null;
        Action effects = null;
        if (json.has("effects")) {
            effects = Actions.parse(json.get("effects"));
            if (effects == null) throw new IllegalArgumentException("'effects' is not a valid Pheno action");
        }
        return new Food(anyFood, items, tags, excludeItems, excludeTags, nutrition, saturation, remainder, effects);
    }

    /** Fills ids and {@code #tags}; returns whether the {@code "*"} selector appeared. */
    private static boolean selectors(JsonObject json, String key, Set<ResourceLocation> items,
                                     Set<ResourceLocation> tags, boolean allowAny) {
        if (!json.has(key)) return false;
        if (!json.get(key).isJsonArray()) throw new IllegalArgumentException("'" + key + "' must be an array");
        boolean any = false;
        for (JsonElement element : json.getAsJsonArray(key)) {
            String raw = element.getAsString().trim();
            if (ANY_FOOD.equals(raw)) {
                if (!allowAny) throw new IllegalArgumentException("'*' is only valid in 'items'");
                any = true;
            } else if (raw.startsWith("#")) {
                tags.add(id(raw.substring(1)));
            } else {
                items.add(id(raw));
            }
        }
        return any;
    }

    private static ResourceLocation id(String raw) {
        ResourceLocation id = DataPackLang.parseId(raw);
        if (id == null) throw new IllegalArgumentException("'" + raw + "' is not a resource id");
        return id;
    }
}
