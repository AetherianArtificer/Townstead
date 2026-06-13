package com.aetherianartificer.townstead.origin.port;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Translates an Apoli entity-condition JSON into the Townstead {@code condition}
 * subset (see {@code origin/condition}). Returns {@code null} for anything outside
 * the subset, so the porting tool can drop the whole gated power rather than emit a
 * half-correct gate. Used only by the offline {@code /townstead origins port} tool.
 */
public final class ApoliConditionTranslator {

    /** Set by {@link PowerToGeneConverter#convert} so {@code power_active} can derive the toggle gene id. */
    static String geneNamespace = "townstead_origins";

    private ApoliConditionTranslator() {}

    @Nullable
    public static JsonObject translate(@Nullable JsonObject apoli) {
        if (apoli == null) return null;
        String type = stripNamespace(GsonHelper.getAsString(apoli, "type", ""));
        JsonObject out = base(type, apoli);
        if (out == null) return null;
        if (GsonHelper.getAsBoolean(apoli, "inverted", false)) out.addProperty("inverted", true);
        return out;
    }

    @Nullable
    private static JsonObject base(String type, JsonObject apoli) {
        switch (type) {
            // Field-less states (namespace already stripped)
            case "in_rain": return simple("in_rain");
            case "on_fire": return simple("on_fire");
            case "sneaking": return simple("sneaking");
            case "sprinting": return simple("sprinting");
            case "moving": return simple("moving");
            case "daytime": return simple("daytime");
            case "exposed_to_sky": return simple("exposed_to_sky");
            case "exposed_to_sun": return simple("exposed_to_sun");
            case "on_ground": return simple("on_ground");
            case "grounded": return simple("grounded");
            case "in_water": return simple("in_water");
            case "swimming": return simple("swimming");
            case "glowing": return simple("glowing");
            case "invisible": return simple("invisible");
            case "using_item": return simple("using_item");
            case "fall_flying": return simple("fall_flying");
            case "climbing": return simple("climbing");
            case "crawling": return simple("crawling");
            case "exists": return simple("exists");
            case "raining": return simple("raining");
            case "thundering": return simple("thundering");
            case "hostile": return simple("hostile");
            case "in_snow": return simple("in_snow");
            case "submerged_in": return apoli.has("fluid")
                    ? copyString(apoli, "fluid", "pheno:submerged_in", "fluid") : simple("submerged");
            // Value comparisons (Apoli {comparison, compare_to} -> our min/max)
            case "brightness": return brightness(apoli);
            case "health": return numeric("health", apoli);
            case "max_health": return numeric("max_health", apoli);
            case "air": return numeric("air", apoli);
            case "fall_distance": return numeric("fall_distance", apoli);
            case "food_level": return numeric("food_level", apoli);
            case "saturation_level": return numeric("saturation_level", apoli);
            case "relative_health": {
                JsonObject out = numeric("health", apoli);
                out.addProperty("relative", true);
                return out;
            }
            case "fluid_height": {
                if (!apoli.has("fluid")) return null;
                JsonObject out = numeric("fluid_height", apoli);
                out.addProperty("fluid", GsonHelper.getAsString(apoli, "fluid", ""));
                return out;
            }
            case "passenger_recursive": {
                JsonObject out = simple("passenger_recursive");
                out.addProperty("comparison", GsonHelper.getAsString(apoli, "comparison", ">="));
                out.addProperty("compare_to", GsonHelper.getAsInt(apoli, "compare_to", 1));
                if (apoli.has("bientity_condition") && apoli.get("bientity_condition").isJsonObject()) {
                    JsonObject where = ApoliBiEntityConditionTranslator.translate(apoli.getAsJsonObject("bientity_condition"));
                    if (where != null) out.add("where", where);
                }
                return out;
            }
            case "velocity": {
                JsonObject out = numeric("velocity", apoli);
                if (apoli.has("axis")) out.addProperty("axis", GsonHelper.getAsString(apoli, "axis", "total"));
                return out;
            }
            case "resource": {
                if (!apoli.has("resource")) return null;
                JsonObject out = numeric("resource", apoli);
                out.addProperty("resource", GsonHelper.getAsString(apoli, "resource", ""));
                return out;
            }
            case "compare_resource": {
                if (!apoli.has("resource")) return null;
                JsonObject out = simple("compare_resource");
                out.addProperty("resource", GsonHelper.getAsString(apoli, "resource", ""));
                out.addProperty("comparison", GsonHelper.getAsString(apoli, "comparison", ">="));
                if (apoli.has("compared_to_resource")) {
                    out.addProperty("compared_to_resource", GsonHelper.getAsString(apoli, "compared_to_resource", ""));
                } else {
                    out.addProperty("compare_to", GsonHelper.getAsInt(apoli, "compare_to", 0));
                }
                return out;
            }
            // Id / tag copies
            case "block_collision": {
                JsonObject out = simple("block");
                out.addProperty("x", GsonHelper.getAsInt(apoli, "offset_x", 0));
                out.addProperty("y", GsonHelper.getAsInt(apoli, "offset_y", 0));
                out.addProperty("z", GsonHelper.getAsInt(apoli, "offset_z", 0));
                JsonObject bc = new JsonObject();
                bc.addProperty("type", "pheno:movement_blocking");
                out.add("block_condition", bc);
                return out;
            }
            case "dimension": return copyString(apoli, "dimension", "pheno:dimension", "dimension");
            case "biome": return biome(apoli);
            case "entity_type": return copyString(apoli, "entity_type", "pheno:entity_type", "entity_type");
            case "gamemode": return copyString(apoli, "gamemode", "pheno:gamemode", "gamemode");
            case "entity_group": return copyString(apoli, "group", "pheno:entity_group", "group");
            case "structure": return copyString(apoli, "structure", "pheno:structure", "structure");
            case "set_size": {
                String set = GsonHelper.getAsString(apoli, "set", "");
                if (set.isEmpty()) return null;
                JsonObject out = simple("collection_size");
                out.addProperty("collection", set);
                out.addProperty("comparison", GsonHelper.getAsString(apoli, "comparison", ">="));
                out.addProperty("compare_to", GsonHelper.getAsInt(apoli, "compare_to", 0));
                return out;
            }
            case "dimensions": {
                JsonObject out = numeric("dimensions", apoli);
                out.addProperty("which", whichFromSet(apoli));
                return out;
            }
            case "scale": {
                JsonObject out = numeric("scale", apoli);
                out.addProperty("which", whichFromScaleType(apoli));
                return out;
            }
            case "on_cooldown": return copyString(apoli, "item", "pheno:on_cooldown", "item");
            case "status_effect": return copyString(apoli, "effect", "pheno:status_effect", "effect");
            case "status_effect_tag": {
                if (!apoli.has("tag")) return null;
                JsonObject out = copyString(apoli, "tag", "pheno:status_effect_tag", "tag");
                if (apoli.has("min_count")) out.addProperty("min_count", GsonHelper.getAsInt(apoli, "min_count", 1));
                return out;
            }
            // Metas
            case "and": return composite("pheno:and", apoli);
            case "or": return composite("pheno:or", apoli);
            case "constant": {
                JsonObject out = simple("constant");
                out.addProperty("value", GsonHelper.getAsBoolean(apoli, "value", true));
                return out;
            }
            case "power_active":
            case "power_type": {
                String power = GsonHelper.getAsString(apoli, "power", "");
                if (power.isEmpty()) return null;
                JsonObject out = simple("toggled");
                out.addProperty("gene", PowerToGeneConverter.geneIdString(geneNamespace, power));
                return out;
            }
            default: return null;
        }
    }

    /** Maps an Apoli {@code {comparison, compare_to}} value test onto our {@code min}/{@code max}. */
    private static JsonObject numeric(String name, JsonObject apoli) {
        JsonObject out = simple(name);
        String comparison = GsonHelper.getAsString(apoli, "comparison", ">=");
        double compareTo = GsonHelper.getAsDouble(apoli, "compare_to", 0d);
        if (comparison.startsWith(">")) out.addProperty("min", compareTo);
        else if (comparison.startsWith("<")) out.addProperty("max", compareTo);
        else if (comparison.startsWith("=")) { out.addProperty("min", compareTo); out.addProperty("max", compareTo); }
        return out;
    }

    private static JsonObject simple(String name) {
        JsonObject out = new JsonObject();
        out.addProperty("type", "pheno:" + name);
        return out;
    }

    private static JsonObject brightness(JsonObject apoli) {
        // Apoli compares a value: {"comparison":">=","compare_to":7}.
        String comparison = GsonHelper.getAsString(apoli, "comparison", ">=");
        float compareTo = GsonHelper.getAsFloat(apoli, "compare_to", 0f);
        JsonObject out = simple("brightness");
        if (comparison.startsWith(">")) out.addProperty("min", (int) Math.ceil(compareTo));
        else if (comparison.startsWith("<")) out.addProperty("max", (int) Math.floor(compareTo));
        else { out.addProperty("min", (int) compareTo); out.addProperty("max", (int) compareTo); }
        return out;
    }

    private static JsonObject copyString(JsonObject apoli, String fromKey, String type, String toKey) {
        if (!apoli.has(fromKey)) return null;
        JsonObject out = new JsonObject();
        out.addProperty("type", type);
        out.addProperty(toKey, GsonHelper.getAsString(apoli, fromKey, ""));
        return out;
    }

    @Nullable
    private static JsonObject composite(String type, JsonObject apoli) {
        JsonArray source = GsonHelper.getAsJsonArray(apoli, "conditions", new JsonArray());
        JsonArray translated = new JsonArray();
        for (var element : source) {
            if (!element.isJsonObject()) return null;
            JsonObject child = translate(element.getAsJsonObject());
            if (child == null) return null;
            translated.add(child);
        }
        JsonObject out = new JsonObject();
        out.addProperty("type", type);
        out.add("conditions", translated);
        return out;
    }

    /**
     * Apoli's entity {@code biome} condition: a single {@code biome} id, a {@code biomes} list (an OR),
     * or a nested biome sub-{@code condition} ({@code category}/{@code in_tag}/{@code temperature}/
     * {@code precipitation}). {@code category} is the deprecated tag alias, so it maps to a biome tag.
     */
    @Nullable
    private static JsonObject biome(JsonObject apoli) {
        if (apoli.has("condition") && apoli.get("condition").isJsonObject()) {
            return biomeSubCondition(apoli.getAsJsonObject("condition"));
        }
        if (apoli.has("biome")) {
            JsonObject out = simple("biome");
            out.addProperty("biome", GsonHelper.getAsString(apoli, "biome", ""));
            return out;
        }
        if (apoli.has("biomes") && apoli.get("biomes").isJsonArray()) {
            JsonArray ids = apoli.getAsJsonArray("biomes");
            if (ids.isEmpty()) return null;
            if (ids.size() == 1) {
                JsonObject out = simple("biome");
                out.addProperty("biome", ids.get(0).getAsString());
                return out;
            }
            JsonArray conditions = new JsonArray();
            for (JsonElement id : ids) {
                JsonObject one = simple("biome");
                one.addProperty("biome", id.getAsString());
                conditions.add(one);
            }
            JsonObject or = new JsonObject();
            or.addProperty("type", "pheno:or");
            or.add("conditions", conditions);
            return or;
        }
        return null;
    }

    /** An Apoli biome sub-condition to a pheno:biome entity condition (tag for category/in_tag). */
    @Nullable
    private static JsonObject biomeSubCondition(JsonObject sub) {
        String type = stripNamespace(GsonHelper.getAsString(sub, "type", ""));
        switch (type) {
            case "category": {
                String category = GsonHelper.getAsString(sub, "category", "");
                if (category.isEmpty()) return null;
                JsonObject out = simple("biome");
                out.addProperty("biome_tag", categoryTag(category));
                return out;
            }
            case "in_tag": {
                String tag = GsonHelper.getAsString(sub, "tag", "");
                if (tag.isEmpty()) return null;
                JsonObject out = simple("biome");
                out.addProperty("biome_tag", tag);
                return out;
            }
            case "temperature": {
                JsonObject inner = new JsonObject();
                inner.addProperty("type", "pheno:temperature");
                String comparison = GsonHelper.getAsString(sub, "comparison", ">=");
                float compareTo = GsonHelper.getAsFloat(sub, "compare_to", 0f);
                if (comparison.startsWith(">")) inner.addProperty("min", compareTo);
                else if (comparison.startsWith("<")) inner.addProperty("max", compareTo);
                else { inner.addProperty("min", compareTo); inner.addProperty("max", compareTo); }
                JsonObject out = simple("biome");
                out.add("condition", inner);
                return out;
            }
            case "precipitation": {
                JsonObject inner = new JsonObject();
                inner.addProperty("type", "pheno:precipitation");
                inner.addProperty("precipitation", GsonHelper.getAsString(sub, "precipitation", "rain").toLowerCase(Locale.ROOT));
                JsonObject out = simple("biome");
                out.add("condition", inner);
                return out;
            }
            // high_humidity is intentionally not translated: reading biome downfall needs a private
            // accessor with no uniform public API in modern MC (see pheno BiomeConditions). Skip-logged.
            default: return null;
        }
    }

    /**
     * Apoli's deprecated biome category to a modern biome tag: the geographic categories with an exact
     * vanilla {@code is_*} tag map there; the rest fall back to Apoli's own {@code apoli:category/*} tag
     * (faithful, but needs those tags present).
     */
    private static String categoryTag(String category) {
        return CATEGORY_TAGS.getOrDefault(category.toLowerCase(Locale.ROOT),
                "apoli:category/" + category.toLowerCase(Locale.ROOT));
    }

    private static final java.util.Map<String, String> CATEGORY_TAGS = java.util.Map.ofEntries(
            java.util.Map.entry("forest", "minecraft:is_forest"),
            java.util.Map.entry("ocean", "minecraft:is_ocean"),
            java.util.Map.entry("river", "minecraft:is_river"),
            java.util.Map.entry("beach", "minecraft:is_beach"),
            java.util.Map.entry("taiga", "minecraft:is_taiga"),
            java.util.Map.entry("jungle", "minecraft:is_jungle"),
            java.util.Map.entry("savanna", "minecraft:is_savanna"),
            java.util.Map.entry("mesa", "minecraft:is_badlands"),
            java.util.Map.entry("badlands", "minecraft:is_badlands"),
            java.util.Map.entry("extreme_hills", "minecraft:is_mountain"),
            java.util.Map.entry("mountain", "minecraft:is_mountain"),
            java.util.Map.entry("nether", "minecraft:is_nether"),
            java.util.Map.entry("the_end", "minecraft:is_end"),
            java.util.Map.entry("end", "minecraft:is_end"));

    /** Apugli's {@code dimensions} enum set (width/height) to our {@code which} ({@code both} by default). */
    static String whichFromSet(JsonObject apoli) {
        if (!apoli.has("dimensions")) return "both";
        boolean width = false;
        boolean height = false;
        JsonElement set = apoli.get("dimensions");
        if (set.isJsonArray()) {
            for (JsonElement e : set.getAsJsonArray()) {
                String s = e.getAsString().toLowerCase(Locale.ROOT);
                if (s.contains("width")) width = true;
                if (s.contains("height")) height = true;
            }
        } else if (set.isJsonPrimitive()) {
            String s = set.getAsString().toLowerCase(Locale.ROOT);
            width = s.contains("width");
            height = s.contains("height");
        }
        if (width && !height) return "width";
        if (height && !width) return "height";
        return "both";
    }

    /** Pehkui scale_type/scale_types ids to our {@code which}: width/height by name, else both. */
    static String whichFromScaleType(JsonObject apoli) {
        boolean width = false;
        boolean height = false;
        for (String key : new String[]{"scale_type", "scale_types"}) {
            if (!apoli.has(key)) continue;
            JsonElement value = apoli.get(key);
            if (value.isJsonArray()) {
                for (JsonElement e : value.getAsJsonArray()) {
                    String s = e.getAsString().toLowerCase(Locale.ROOT);
                    if (s.contains("width")) width = true;
                    if (s.contains("height")) height = true;
                }
            } else if (value.isJsonPrimitive()) {
                String s = value.getAsString().toLowerCase(Locale.ROOT);
                if (s.contains("width")) width = true;
                if (s.contains("height")) height = true;
            }
        }
        if (width && !height) return "width";
        if (height && !width) return "height";
        return "both";
    }

    static String stripNamespace(String type) {
        int colon = type.indexOf(':');
        return (colon < 0 ? type : type.substring(colon + 1)).toLowerCase(Locale.ROOT);
    }
}
