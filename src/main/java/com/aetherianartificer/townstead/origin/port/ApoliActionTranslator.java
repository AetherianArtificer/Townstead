package com.aetherianartificer.townstead.origin.port;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

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
            case "action_on_set": return actionOnSet(apoli);
            case "raycast": return raycast(apoli);
            case "explosion_raycast": return explosionRaycast(apoli);
            case "spawn_effect_cloud":
            case "spawn_custom_effect_cloud": return spawnCloud(apoli);
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
            case "emit_game_event": {
                JsonObject out = typed("emit_game_event");
                out.addProperty("event", GsonHelper.getAsString(apoli, "event", ""));
                return out;
            }
            case "zombify_villager": return typed("zombify_villager");
            case "set_resource": {
                JsonObject out = typed("change_resource");
                out.addProperty("resource", GsonHelper.getAsString(apoli, "resource", ""));
                out.addProperty("amount", GsonHelper.getAsInt(apoli, "value", 0));
                out.addProperty("operation", "set");
                return out;
            }
            case "selector_action": {
                JsonElement inner = translateBiEntity(apoli.get("bientity_action"));
                if (inner == null || !inner.isJsonObject()) return null;
                JsonObject selector = new JsonObject();
                selector.addProperty("type", "pheno:command");
                selector.addProperty("selector", GsonHelper.getAsString(apoli, "selector", "@e"));
                inner.getAsJsonObject().add("on", selector);
                return inner;
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
     * {@code remove_from_set}) maps to {@code change_collection} on the {@code set}'s
     * collection now that Pheno has the collection family.
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
            case "add_to_set": return changeCollection(apoli, "add");
            case "remove_from_set": return changeCollection(apoli, "remove");
            case "change_hits_on_target": return changeHitsOnTarget(apoli);
            case "raycast_between": return raycastBetween(apoli);
            case "spawn_custom_effect_cloud": return spawnCloudBiEntity(apoli);
            default: return null;
        }
    }

    /** add_to_set / remove_from_set -> change_collection on the actor's collection (the {@code set}). */
    @Nullable
    private static JsonElement changeCollection(JsonObject apoli, String operation) {
        String set = GsonHelper.getAsString(apoli, "set", "");
        if (set.isEmpty()) return null;
        JsonObject out = typed("change_collection");
        out.addProperty("collection", set);
        out.addProperty("operation", operation);
        if (apoli.has("time_limit")) out.addProperty("time_limit", GsonHelper.getAsInt(apoli, "time_limit", 0));
        return out;
    }

    /**
     * change_hits_on_target -> change_collection on the implicit hits-on-target counter. Apugli's
     * {@code change}/{@code operation} become {@code amount}/{@code operation}; the per-pair idle
     * timer is the collection's forget_after, so timer_change/timer_operation are dropped.
     */
    private static JsonElement changeHitsOnTarget(JsonObject apoli) {
        JsonObject out = typed("change_collection");
        out.addProperty("collection", PowerToGeneConverter.HITS_ON_TARGET_COLLECTION);
        boolean set = "set".equalsIgnoreCase(GsonHelper.getAsString(apoli, "operation", "add"));
        out.addProperty("operation", set ? "set" : "add");
        out.addProperty("amount", GsonHelper.getAsInt(apoli, "change", 0));
        return out;
    }

    /** action_on_set -> for_each over the collection, running the bientity action per member. */
    @Nullable
    private static JsonElement actionOnSet(JsonObject apoli) {
        String set = GsonHelper.getAsString(apoli, "set", "");
        JsonElement inner = translateBiEntity(apoli.get("bientity_action"));
        if (set.isEmpty() || inner == null) return null;
        JsonObject out = typed("for_each");
        out.addProperty("in", set);
        out.add("do", inner);
        if (apoli.has("bientity_condition") && apoli.get("bientity_condition").isJsonObject()) {
            JsonObject where = ApoliBiEntityConditionTranslator.translate(apoli.getAsJsonObject("bientity_condition"));
            if (where != null) out.add("where", where);
        }
        if (apoli.has("limit")) out.addProperty("limit", GsonHelper.getAsInt(apoli, "limit", 0));
        out.addProperty("order", GsonHelper.getAsBoolean(apoli, "reverse", false) ? "newest_first" : "oldest_first");
        return out;
    }

    /**
     * raycast -> the beam (if it draws particles) plus the bientity action run on what the ray hits
     * ({@code on: pheno:ray}). Block sub-actions are not translated (there is no Apoli block-action
     * translator yet); the native pheno:ray/pheno:at let an author wire those by hand.
     */
    @Nullable
    private static JsonElement raycast(JsonObject apoli) {
        JsonArray out = new JsonArray();
        JsonElement beam = beam(apoli, false);
        if (beam != null) out.add(beam);
        if (apoli.has("bientity_action")) {
            JsonElement inner = translateBiEntity(apoli.get("bientity_action"));
            if (inner != null && inner.isJsonObject()) {
                inner.getAsJsonObject().add("on", rayObject(apoli, "entity", false));
                out.add(inner);
            }
        }
        JsonElement block = blockAt(apoli, false);
        if (block != null) out.add(block);
        return out.isEmpty() ? null : (out.size() == 1 ? out.get(0) : out);
    }

    /** block_action run at the ray's block hit, via pheno:at over the block-form pheno:ray selector. */
    @Nullable
    private static JsonElement blockAt(JsonObject apoli, boolean toward) {
        if (!apoli.has("block_action")) return null;
        JsonElement inner = ApoliBlockActionTranslator.translate(apoli.get("block_action"));
        if (inner == null) return null;
        JsonObject at = typed("at");
        at.add("blocks", rayObject(apoli, "block", toward));
        at.add("do", inner);
        return at;
    }

    /** explosion_raycast -> explode at the ray's block hit, plus the beam. */
    @Nullable
    private static JsonElement explosionRaycast(JsonObject apoli) {
        JsonObject explode = typed("explode");
        if (apoli.has("power")) explode.addProperty("power", GsonHelper.getAsFloat(apoli, "power", 2.0f));
        explode.addProperty("fire", GsonHelper.getAsBoolean(apoli, "create_fire", false));
        String destruction = GsonHelper.getAsString(apoli, "destruction_type", "keep").toLowerCase(Locale.ROOT);
        explode.addProperty("destroy", destruction.contains("destroy") || destruction.contains("break"));
        JsonObject at = typed("at");
        at.add("blocks", rayObject(apoli, "block", false));
        at.add("do", explode);
        JsonElement beam = beam(apoli, false);
        if (beam == null) return at;
        JsonArray out = new JsonArray();
        out.add(at);
        out.add(beam);
        return out;
    }

    /**
     * raycast_between -> the beam toward the target, plus its block_action run at the block in the way
     * (pheno:at over a target-aimed block ray), now that block actions translate.
     */
    @Nullable
    private static JsonElement raycastBetween(JsonObject apoli) {
        JsonArray out = new JsonArray();
        JsonElement beam = beam(apoli, true);
        if (beam != null) out.add(beam);
        JsonElement block = blockAt(apoli, true);
        if (block != null) out.add(block);
        return out.isEmpty() ? null : (out.size() == 1 ? out.get(0) : out);
    }

    /**
     * spawn_effect_cloud / spawn_custom_effect_cloud -> a pheno:cloud. Its {@code do} is the owner's
     * action on the entities inside, taken from {@code owner_target_bientity_action} (wrapped in
     * {@code invert}, since the cloud runs do as inside=entity()/owner=other()), or the status effect
     * for the Apoli base. powers_to_apply and the cloud-as-actor hooks are not modeled.
     */
    @Nullable
    private static JsonElement spawnCloud(JsonObject apoli) {
        JsonObject out = typed("cloud");
        JsonElement doAction;
        if (apoli.has("owner_target_bientity_action")) {
            JsonElement inner = translateBiEntity(apoli.get("owner_target_bientity_action"));
            doAction = inner == null ? null : invertWrap(inner);
        } else {
            doAction = applyEffect(apoli);
        }
        if (doAction == null) return null;
        out.add("do", doAction);
        copyNumber(apoli, "radius", out, "radius");
        copyNumber(apoli, "radius_per_tick", out, "grow_per_tick");
        copyNumber(apoli, "radius_on_use", out, "shrink_on_use");
        copyNumber(apoli, "wait_time", out, "wait_time");
        copyNumber(apoli, "duration", out, "duration");
        copyNumber(apoli, "reapplication_delay", out, "reapply_delay");
        copyNumber(apoli, "height_increase", out, "height");
        String particle = particleId(apoli.get("particle"));
        if (particle != null) out.addProperty("particle", particle);
        if (apoli.has("owner_target_bientity_condition") && apoli.get("owner_target_bientity_condition").isJsonObject()) {
            JsonObject where = ApoliBiEntityConditionTranslator.translate(apoli.getAsJsonObject("owner_target_bientity_condition"));
            if (where != null) out.add("where", where);
        }
        return out;
    }

    /** The bi-entity variant spawns at the actor or target (spawn_target); the bearer stays owner. */
    @Nullable
    private static JsonElement spawnCloudBiEntity(JsonObject apoli) {
        JsonElement cloud = spawnCloud(apoli);
        if (!(cloud instanceof JsonObject c)) return null;
        c.addProperty("on", GsonHelper.getAsString(apoli, "spawn_target", "target").equalsIgnoreCase("actor")
                ? "self" : "target");
        JsonObject wrap = typed("actor_action");
        wrap.add("action", c);
        return wrap;
    }

    private static JsonObject invertWrap(JsonElement inner) {
        JsonObject out = typed("invert");
        out.add("action", inner);
        return out;
    }

    private static void copyNumber(JsonObject from, String fromKey, JsonObject to, String toKey) {
        if (from.has(fromKey) && from.get(fromKey).isJsonPrimitive()) {
            to.addProperty(toKey, GsonHelper.getAsDouble(from, fromKey, 0));
        }
    }

    /** The pheno:ray selector object from Apoli's shared ray fields. */
    private static JsonObject rayObject(JsonObject apoli, String stopOn, boolean toward) {
        JsonObject ray = new JsonObject();
        ray.addProperty("type", "pheno:ray");
        if (apoli.has("distance") && apoli.get("distance").isJsonPrimitive()) {
            ray.addProperty("distance", GsonHelper.getAsDouble(apoli, "distance", 0));
        }
        JsonArray dir = apoli.has("direction") ? vector(apoli.get("direction")) : null;
        if (dir != null) ray.add("direction", dir);
        if (apoli.has("space")) ray.addProperty("space",
                GsonHelper.getAsString(apoli, "space", "world").toLowerCase(Locale.ROOT).contains("local") ? "local" : "world");
        if (apoli.has("pierce")) ray.addProperty("pierce", GsonHelper.getAsBoolean(apoli, "pierce", false));
        if (toward) ray.addProperty("toward", "target");
        ray.addProperty("stop_on", stopOn);
        return ray;
    }

    /** pheno:beam carrying the same ray config, when the source draws particles. */
    @Nullable
    private static JsonObject beam(JsonObject apoli, boolean toward) {
        String particle = particleId(apoli.get("particle"));
        if (particle == null) return null;
        JsonObject out = typed("beam");
        out.addProperty("particle", particle);
        if (apoli.has("spacing")) out.addProperty("spacing", GsonHelper.getAsDouble(apoli, "spacing", 0.5));
        if (apoli.has("distance") && apoli.get("distance").isJsonPrimitive()) {
            out.addProperty("distance", GsonHelper.getAsDouble(apoli, "distance", 0));
        }
        JsonArray dir = apoli.has("direction") ? vector(apoli.get("direction")) : null;
        if (dir != null) out.add("direction", dir);
        if (apoli.has("space")) out.addProperty("space",
                GsonHelper.getAsString(apoli, "space", "world").toLowerCase(Locale.ROOT).contains("local") ? "local" : "world");
        if (toward) out.addProperty("toward", "target");
        return out;
    }

    @Nullable
    private static JsonArray vector(JsonElement element) {
        if (element.isJsonArray()) return element.getAsJsonArray();
        if (element.isJsonObject()) {
            JsonObject o = element.getAsJsonObject();
            JsonArray a = new JsonArray();
            a.add(GsonHelper.getAsDouble(o, "x", 0));
            a.add(GsonHelper.getAsDouble(o, "y", 0));
            a.add(GsonHelper.getAsDouble(o, "z", 0));
            return a;
        }
        return null;
    }

    @Nullable
    private static String particleId(@Nullable JsonElement element) {
        if (element == null) return null;
        if (element.isJsonPrimitive()) return element.getAsString();
        if (element.isJsonObject() && element.getAsJsonObject().has("type")) {
            return GsonHelper.getAsString(element.getAsJsonObject(), "type", "");
        }
        return null;
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
        out.addProperty("type", "pheno:" + name);
        return out;
    }
}
