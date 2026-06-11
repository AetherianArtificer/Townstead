package com.aetherianartificer.townstead.origin.port;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

/**
 * Translates an Apoli entity-action JSON into the Townstead {@code action} subset
 * (see {@code origin/action}), for porting {@code active_self} powers, plus
 * {@link #translateBiEntity} for the actor/target {@code bientity_action} subset.
 * Returns {@code null} outside the subset so the whole power is skip-logged rather than
 * half-converted. Offline tool only.
 */
public final class ApoliActionTranslator {

    private ApoliActionTranslator() {}

    @Nullable
    public static JsonElement translate(@Nullable JsonElement element) {
        if (element == null) return null;
        if (element.isJsonArray()) {
            JsonArray out = new JsonArray();
            for (JsonElement child : element.getAsJsonArray()) {
                JsonElement translated = translate(child);
                if (translated == null) return null;
                out.add(translated);
            }
            return out.isEmpty() ? null : out;
        }
        if (!element.isJsonObject()) return null;
        JsonObject apoli = element.getAsJsonObject();
        String type = ApoliConditionTranslator.stripNamespace(GsonHelper.getAsString(apoli, "type", ""));
        return base(type, apoli);
    }

    @Nullable
    private static JsonElement base(String type, JsonObject apoli) {
        switch (type) {
            case "add_velocity": {
                JsonObject out = typed("add_velocity");
                out.addProperty("x", GsonHelper.getAsDouble(apoli, "x", 0d));
                out.addProperty("y", GsonHelper.getAsDouble(apoli, "y", 0d));
                out.addProperty("z", GsonHelper.getAsDouble(apoli, "z", 0d));
                out.addProperty("relative", GsonHelper.getAsString(apoli, "space", "world").contains("local"));
                return out;
            }
            case "heal": {
                JsonObject out = typed("heal");
                out.addProperty("amount", GsonHelper.getAsFloat(apoli, "amount", 0f));
                return out;
            }
            case "damage": {
                JsonObject out = typed("damage");
                out.addProperty("amount", GsonHelper.getAsFloat(apoli, "amount", 0f));
                return out;
            }
            case "set_on_fire": {
                JsonObject out = typed("ignite");
                out.addProperty("seconds", Math.max(1, GsonHelper.getAsInt(apoli, "duration", 60) / 20));
                return out;
            }
            case "extinguish": return typed("extinguish");
            case "apply_effect": return applyEffect(apoli);
            case "exhaust": {
                JsonObject out = typed("exhaust");
                out.addProperty("amount", GsonHelper.getAsFloat(apoli, "amount", 0f));
                return out;
            }
            case "change_resource": {
                JsonObject out = typed("change_resource");
                out.addProperty("resource", GsonHelper.getAsString(apoli, "resource", ""));
                out.addProperty("amount", GsonHelper.getAsInt(apoli, "change", GsonHelper.getAsInt(apoli, "amount", 0)));
                out.addProperty("operation", GsonHelper.getAsString(apoli, "operation", "add"));
                return out;
            }
            case "execute_command": {
                JsonObject out = typed("execute_command");
                out.addProperty("command", GsonHelper.getAsString(apoli, "command", ""));
                return out;
            }
            case "spawn_entity": {
                JsonObject out = typed("spawn_entity");
                out.addProperty("entity", GsonHelper.getAsString(apoli, "entity_type", ""));
                return out;
            }
            case "give": {
                JsonObject stack = apoli.has("stack") && apoli.get("stack").isJsonObject()
                        ? apoli.getAsJsonObject("stack") : null;
                if (stack == null || !stack.has("item")) return null;
                JsonObject out = typed("give");
                out.addProperty("item", GsonHelper.getAsString(stack, "item", ""));
                out.addProperty("count", GsonHelper.getAsInt(stack, "count", 1));
                return out;
            }
            case "set_no_gravity": {
                JsonObject out = typed("set_no_gravity");
                out.addProperty("gravity", GsonHelper.getAsBoolean(apoli, "gravity", false));
                return out;
            }
            case "item_cooldown": {
                JsonObject out = typed("item_cooldown");
                out.addProperty("item", GsonHelper.getAsString(apoli, "item", ""));
                out.addProperty("cooldown", GsonHelper.getAsInt(apoli, "cooldown", 20));
                return out;
            }
            case "and": return translate(apoli.get("actions"));
            default: return null;
        }
    }

    /**
     * Translates an Apoli bi-entity action (actor/target pair) into the Townstead
     * subset: {@code actor_action} / {@code target_action} wrap entity actions,
     * {@code invert} swaps roles, and {@code damage} / {@code add_velocity} are folded
     * into a {@code target_action} (they hit the target). {@code mount} / {@code tame}
     * / {@code set_in_love} map directly. Set membership ({@code add_to_set} /
     * {@code remove_from_set}) is plumbing and returns {@code null} (skip-logged).
     */
    @Nullable
    public static JsonElement translateBiEntity(@Nullable JsonElement element) {
        if (element == null) return null;
        if (element.isJsonArray()) {
            JsonArray out = new JsonArray();
            for (JsonElement child : element.getAsJsonArray()) {
                JsonElement translated = translateBiEntity(child);
                if (translated == null) return null;
                out.add(translated);
            }
            return out.isEmpty() ? null : out;
        }
        if (!element.isJsonObject()) return null;
        JsonObject apoli = element.getAsJsonObject();
        String type = ApoliConditionTranslator.stripNamespace(GsonHelper.getAsString(apoli, "type", ""));
        switch (type) {
            case "actor_action": {
                JsonElement inner = translate(apoli.get("action"));
                if (inner == null) return null;
                JsonObject out = typed("actor_action");
                out.add("action", inner);
                return out;
            }
            case "target_action": {
                JsonElement inner = translate(apoli.get("action"));
                if (inner == null) return null;
                JsonObject out = typed("target_action");
                out.add("action", inner);
                return out;
            }
            case "invert": {
                JsonElement inner = translateBiEntity(apoli.get("action"));
                if (inner == null) return null;
                JsonObject out = typed("invert");
                out.add("action", inner);
                return out;
            }
            case "and": return translateBiEntity(apoli.get("actions"));
            case "damage": {
                JsonObject dmg = typed("damage");
                dmg.addProperty("amount", GsonHelper.getAsFloat(apoli, "amount", 0f));
                JsonObject out = typed("target_action");
                out.add("action", dmg);
                return out;
            }
            case "add_velocity": {
                JsonObject vel = typed("add_velocity");
                vel.addProperty("x", GsonHelper.getAsDouble(apoli, "x", 0d));
                vel.addProperty("y", GsonHelper.getAsDouble(apoli, "y", 0d));
                vel.addProperty("z", GsonHelper.getAsDouble(apoli, "z", 0d));
                vel.addProperty("relative", GsonHelper.getAsString(apoli, "space", "world").contains("local"));
                JsonObject out = typed("target_action");
                out.add("action", vel);
                return out;
            }
            case "mount": return typed("mount");
            case "tame": return typed("tame");
            case "set_in_love": return typed("set_in_love");
            default: return null;
        }
    }

    @Nullable
    private static JsonElement applyEffect(JsonObject apoli) {
        JsonObject effect = null;
        if (apoli.has("effect") && apoli.get("effect").isJsonObject()) {
            effect = apoli.getAsJsonObject("effect");
        } else if (apoli.has("effects") && apoli.get("effects").isJsonArray()) {
            JsonArray effects = apoli.getAsJsonArray("effects");
            if (!effects.isEmpty() && effects.get(0).isJsonObject()) effect = effects.get(0).getAsJsonObject();
        }
        if (effect == null) return null;
        String id = GsonHelper.getAsString(effect, "effect", "");
        if (id.isEmpty()) return null;
        JsonObject out = typed("apply_effect");
        out.addProperty("effect", id);
        out.addProperty("duration", GsonHelper.getAsInt(effect, "duration", 200));
        out.addProperty("amplifier", GsonHelper.getAsInt(effect, "amplifier", 0));
        return out;
    }

    private static JsonObject typed(String name) {
        JsonObject out = new JsonObject();
        out.addProperty("type", "townstead_origins:" + name);
        return out;
    }
}
