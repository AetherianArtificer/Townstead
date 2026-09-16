package com.aetherianartificer.townstead.clothing.policy;

import com.aetherianartificer.townstead.clothing.ClothingLayer;
import com.aetherianartificer.townstead.clothing.ClothingQuery;
import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * What each layer should hold under a condition.
 *
 * <pre>{@code
 * {
 *   "schema": "townstead:wardrobe_policy/v1",
 *   "scope": "village",
 *   "priority": 10,
 *   "when": { "type": "pheno:environment", "season": "winter" },
 *   "layers": {
 *     "base":      { "select": { "thermal": "warm" } },
 *     "outerwear": { "require": "required", "select": { "thermal": "warm", "slot": "body" } },
 *     "accessory": { "require": "preferred", "select": { "thermal": "warm", "slot": "head" } }
 *   }
 * }
 * }</pre>
 *
 * <p>A layer rule names one selector: a set id, {@code "<culture>"} for the villager's culture
 * sets, {@code "<body>"} for what fits the body, a skin pattern, or a query. Armour is never a
 * policy's to set; a rule for it is refused at load.</p>
 */
public record WardrobePolicy(ResourceLocation id,
                             Scope scope,
                             Set<ResourceLocation> cultures,
                             int priority,
                             @Nullable Condition when,
                             Map<ClothingLayer, LayerRule> layers) {

    public static final String SCHEMA = "townstead:wardrobe_policy/v1";

    public enum Scope {
        /** Every villager. */
        VILLAGE(3),
        /** Villagers whose stored culture is listed. */
        CULTURE(2),
        /** One resident, set through a screen; reserved, applies to nobody from data. */
        VILLAGER(1),
        /** Workers on shift at a worksite; reserved until worksite membership is readable here. */
        WORKSITE(0);

        /** Lower wins when priorities tie. */
        public final int rank;

        Scope(int rank) {
            this.rank = rank;
        }

        public static @Nullable Scope parse(@Nullable String raw) {
            if (raw == null) return null;
            try {
                return valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    public enum Requirement { REQUIRED, PREFERRED, NONE }

    /** One layer's requirement and how to choose what fills it. */
    public record LayerRule(Requirement requirement, Selector selector) {}

    /**
     * How a rule names candidates. Exactly one field is set; {@link #cultureSets} and
     * {@link #bodySets} are the two markers a data author writes as {@code "<culture>"} and
     * {@code "<body>"}.
     */
    public record Selector(@Nullable ResourceLocation set,
                           boolean cultureSets,
                           boolean bodySets,
                           @Nullable String skin,
                           @Nullable ClothingQuery query) {

        public static final Selector ANYTHING = new Selector(null, false, false, null, ClothingQuery.ANY);

        public boolean isEmpty() {
            return set == null && !cultureSets && !bodySets && skin == null && (query == null || query.isEmpty());
        }

        /** A skin pattern: exact id, a {@code /} prefix, or a trailing {@code *}. */
        public boolean skinMatches(@Nullable String skinId) {
            if (skin == null || skinId == null) return false;
            if (skin.endsWith("*")) return skinId.startsWith(skin.substring(0, skin.length() - 1));
            if (skin.endsWith("/")) return skinId.startsWith(skin);
            return skin.equals(skinId);
        }
    }

    public WardrobePolicy {
        cultures = cultures == null ? Set.of() : Set.copyOf(cultures);
        layers = layers == null ? Map.of() : Map.copyOf(layers);
    }

    public @Nullable LayerRule rule(ClothingLayer layer) {
        return layers.get(layer);
    }

    public static @Nullable WardrobePolicy parse(ResourceLocation id, JsonObject json) {
        if (json == null) return null;
        Scope scope = Scope.parse(GsonHelper.getAsString(json, "scope", "village"));
        if (scope == null) return null;

        Set<ResourceLocation> cultures = new LinkedHashSet<>();
        for (String raw : ClothingQuery.strings(json.get("cultures"))) {
            ResourceLocation culture = DataPackLang.parseId(raw);
            if (culture != null) cultures.add(culture);
        }
        if (scope == Scope.CULTURE && cultures.isEmpty()) return null;

        Condition when = json.has("when") ? Conditions.parse(json.get("when")) : null;

        JsonObject layersJson = GsonHelper.getAsJsonObject(json, "layers", null);
        if (layersJson == null) return null;
        Map<ClothingLayer, LayerRule> layers = new EnumMap<>(ClothingLayer.class);
        for (Map.Entry<String, JsonElement> e : layersJson.entrySet()) {
            ClothingLayer layer = ClothingLayer.parse(e.getKey());
            if (layer == null || layer == ClothingLayer.ARMOUR) return null;
            LayerRule rule = parseRule(layer, e.getValue());
            if (rule == null) return null;
            layers.put(layer, rule);
        }
        if (layers.isEmpty()) return null;

        return new WardrobePolicy(id, scope, cultures, GsonHelper.getAsInt(json, "priority", 0), when, layers);
    }

    static @Nullable LayerRule parseRule(ClothingLayer layer, @Nullable JsonElement element) {
        if (element == null || !element.isJsonObject()) return null;
        JsonObject json = element.getAsJsonObject();
        String requireRaw = GsonHelper.getAsString(json, "require",
                layer == ClothingLayer.BASE ? "required" : "preferred").trim().toLowerCase(Locale.ROOT);
        Requirement requirement;
        switch (requireRaw) {
            case "required": requirement = Requirement.REQUIRED; break;
            case "preferred": requirement = Requirement.PREFERRED; break;
            case "none": requirement = Requirement.NONE; break;
            default: return null;
        }
        Selector selector = parseSelector(json);
        if (selector == null) return null;
        // "None" with a selector means "none of these": only pieces it admits are taken off.
        if (requirement == Requirement.NONE) {
            return new LayerRule(requirement, selector.isEmpty() ? Selector.ANYTHING : selector);
        }
        if (layer == ClothingLayer.BASE && selector.isEmpty()) return null;
        return new LayerRule(requirement, selector);
    }

    static @Nullable Selector parseSelector(JsonObject json) {
        String set = GsonHelper.getAsString(json, "set", "").trim();
        String skin = GsonHelper.getAsString(json, "skin", "").trim();
        boolean hasQuery = json.has("select");
        int named = (set.isEmpty() ? 0 : 1) + (skin.isEmpty() ? 0 : 1) + (hasQuery ? 1 : 0);
        if (named > 1) return null;
        if (!set.isEmpty()) {
            if (set.equals("<culture>")) return new Selector(null, true, false, null, null);
            if (set.equals("<body>")) return new Selector(null, false, true, null, null);
            ResourceLocation id = DataPackLang.parseId(set);
            return id == null ? null : new Selector(id, false, false, null, null);
        }
        if (!skin.isEmpty()) return new Selector(null, false, false, skin, null);
        return new Selector(null, false, false, null, ClothingQuery.parse(json.get("select")));
    }
}
