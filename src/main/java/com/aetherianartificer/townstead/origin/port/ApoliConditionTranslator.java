package com.aetherianartificer.townstead.origin.port;

import com.google.gson.JsonArray;
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
            // Field-less states (Apoli + Apugli; namespace already stripped)
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
            case "raining": return simple("raining");
            case "thundering": return simple("thundering");
            case "hostile": return simple("hostile");
            case "in_snow": return simple("in_snow");
            case "submerged_in": return apoli.has("fluid")
                    ? copyString(apoli, "fluid", "townstead_origins:submerged_in", "fluid") : simple("submerged");
            // Value comparisons (Apoli {comparison, compare_to} -> our min/max)
            case "brightness": return brightness(apoli);
            case "health": return numeric("health", apoli);
            case "max_health": return numeric("max_health", apoli);
            case "air": return numeric("air", apoli);
            case "fall_distance": return numeric("fall_distance", apoli);
            case "food_level": return numeric("food_level", apoli);
            case "saturation_level": return numeric("saturation_level", apoli);
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
            case "dimension": return copyString(apoli, "dimension", "townstead_origins:dimension", "dimension");
            case "biome": return apoli.has("biome")
                    ? copyString(apoli, "biome", "townstead_origins:biome", "biome") : null;
            case "entity_type": return copyString(apoli, "entity_type", "townstead_origins:entity_type", "entity_type");
            case "gamemode": return copyString(apoli, "gamemode", "townstead_origins:gamemode", "gamemode");
            case "entity_group": return copyString(apoli, "group", "townstead_origins:entity_group", "group");
            case "structure": return copyString(apoli, "structure", "townstead_origins:structure", "structure");
            case "on_cooldown": return copyString(apoli, "item", "townstead_origins:on_cooldown", "item");
            case "status_effect": return copyString(apoli, "effect", "townstead_origins:status_effect", "effect");
            case "status_effect_tag": {
                if (!apoli.has("tag")) return null;
                JsonObject out = copyString(apoli, "tag", "townstead_origins:status_effect_tag", "tag");
                if (apoli.has("min_count")) out.addProperty("min_count", GsonHelper.getAsInt(apoli, "min_count", 1));
                return out;
            }
            // Metas
            case "and": return composite("townstead_origins:and", apoli);
            case "or": return composite("townstead_origins:or", apoli);
            case "constant": {
                JsonObject out = simple("constant");
                out.addProperty("value", GsonHelper.getAsBoolean(apoli, "value", true));
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
        out.addProperty("type", "townstead_origins:" + name);
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

    static String stripNamespace(String type) {
        int colon = type.indexOf(':');
        return (colon < 0 ? type : type.substring(colon + 1)).toLowerCase(Locale.ROOT);
    }
}
