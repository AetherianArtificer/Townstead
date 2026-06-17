package com.aetherianartificer.townstead.origin.port;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Translates an Apoli/Apugli block-action JSON into the Townstead block-action subset (see
 * {@code pheno/action/block}). Lets the raycast and collection ports carry block effects through to
 * Pheno instead of dropping them. Returns {@code null} outside the subset so the caller can skip the
 * untranslatable part rather than emit a half-correct action. Offline tool only.
 *
 * <p>Block conditions (an area_of_effect/explode filter) are not translated (no Apoli block-condition
 * translator yet); a filtered area is widened to all blocks, flagged here for the author.</p>
 */
public final class ApoliBlockActionTranslator {

    private ApoliBlockActionTranslator() {}

    @Nullable
    public static JsonElement translate(@Nullable JsonElement element) {
        if (element == null) return null;
        if (element.isJsonArray()) {
            JsonArray out = new JsonArray();
            for (JsonElement child : element.getAsJsonArray()) {
                JsonElement translated = translate(child);
                if (translated != null) out.add(translated);
            }
            return out.isEmpty() ? null : (out.size() == 1 ? out.get(0) : out);
        }
        if (!element.isJsonObject()) return null;
        JsonObject apoli = element.getAsJsonObject();
        String type = ApoliConditionTranslator.stripNamespace(GsonHelper.getAsString(apoli, "type", ""));
        return base(type, apoli);
    }

    @Nullable
    private static JsonElement base(String type, JsonObject apoli) {
        switch (type) {
            case "and": return translate(apoli.get("actions"));
            case "area_of_effect": {
                JsonElement inner = translate(apoli.get("block_action"));
                if (inner == null) return null;
                JsonObject out = typed("area_of_effect");
                out.addProperty("radius", GsonHelper.getAsInt(apoli, "radius", 16));
                out.addProperty("shape",
                        GsonHelper.getAsString(apoli, "shape", "cube").toLowerCase(Locale.ROOT).contains("sphere") ? "sphere" : "cube");
                out.add("block_action", inner);
                return out;
            }
            case "bonemeal": return typed("bonemeal");
            case "explode": {
                JsonObject out = typed("explode");
                out.addProperty("power", GsonHelper.getAsFloat(apoli, "power", 2.0f));
                out.addProperty("fire", GsonHelper.getAsBoolean(apoli, "create_fire", false));
                String destruction = GsonHelper.getAsString(apoli, "destruction_type", "destroy").toLowerCase(Locale.ROOT);
                out.addProperty("destroy", !destruction.contains("keep"));
                return out;
            }
            case "modify_block_state": return modifyBlockState(apoli);
            case "spawn_entity": {
                if (!apoli.has("entity_type")) return null;
                JsonObject out = typed("spawn_entity");
                out.addProperty("entity", GsonHelper.getAsString(apoli, "entity_type", ""));
                return out;
            }
            case "destroy": {
                JsonObject out = typed("destroy");
                out.addProperty("drop_item", GsonHelper.getAsBoolean(apoli, "drop_block", true));
                return out;
            }
            case "schedule_tick": {
                JsonObject out = typed("schedule_tick");
                out.addProperty("delay", Math.max(1, GsonHelper.getAsInt(apoli, "min", 1)));
                return out;
            }
            // light_up and the meta chance/delay/if_else/nothing have no block-action equivalent yet.
            default: return null;
        }
    }

    /**
     * modify_block_state: {@code cycle} maps to {@code operation: cycle}; otherwise the new value is the
     * {@code enum}, the boolean {@code value}, or the numeric {@code change}. Apoli's add/subtract
     * increment of a numeric property collapses to a direct set (Pheno assigns rather than increments).
     */
    @Nullable
    private static JsonElement modifyBlockState(JsonObject apoli) {
        String property = GsonHelper.getAsString(apoli, "property", "");
        if (property.isEmpty()) return null;
        JsonObject out = typed("modify_block_state");
        out.addProperty("property", property);
        if (GsonHelper.getAsBoolean(apoli, "cycle", false)) {
            out.addProperty("operation", "cycle");
            return out;
        }
        if (apoli.has("enum") && apoli.get("enum").isJsonPrimitive()) {
            out.addProperty("value", GsonHelper.getAsString(apoli, "enum", ""));
        } else if (apoli.has("value") && apoli.get("value").isJsonPrimitive()) {
            out.addProperty("value", Boolean.toString(GsonHelper.getAsBoolean(apoli, "value", false)));
        } else if (apoli.has("change") && apoli.get("change").isJsonPrimitive()) {
            out.addProperty("value", Integer.toString(GsonHelper.getAsInt(apoli, "change", 0)));
        } else {
            return null;
        }
        return out;
    }

    private static JsonObject typed(String name) {
        JsonObject out = new JsonObject();
        out.addProperty("type", "pheno:" + name);
        return out;
    }
}
