package com.aetherianartificer.townstead.reaction;

import com.aetherianartificer.townstead.Townstead;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * One reaction definition loaded from a data pack JSON. Holds metadata,
 * gating thresholds, declarative triggers (kept as raw {@link JsonObject}
 * for the trigger layer to parse), and the binding list. Bindings are the
 * weighted animation candidates the dispatcher picks from when the
 * reaction fires.
 */
public record Reaction(
        ResourceLocation id,
        Optional<String> displayName,
        List<String> tags,
        int cooldownTicks,
        float chance,
        int lockTicks,
        ReactionConditions conditions,
        int mirrorRadius,
        float mirrorChance,
        List<JsonObject> rawTriggers,
        List<ReactionBinding> bindings) {

    public static Reaction parse(ResourceLocation id, JsonObject json) {
        Optional<String> displayName =
                json.has("display_name") ? Optional.of(GsonHelper.getAsString(json, "display_name")) : Optional.empty();
        List<String> tags = ReactionConditions.parseStringArray(json, "tags");
        int cooldownTicks = Math.max(0, GsonHelper.getAsInt(json, "cooldown_ticks", 100));
        float chance = clamp01(GsonHelper.getAsFloat(json, "chance", 1.0F));
        int lockTicks = Math.max(0, GsonHelper.getAsInt(json, "lock_ticks", 0));
        ReactionConditions conditions = json.has("conditions") && json.get("conditions").isJsonObject()
                ? ReactionConditions.fromJson(json.getAsJsonObject("conditions"))
                : ReactionConditions.EMPTY;
        int mirrorRadius = Math.max(0, GsonHelper.getAsInt(json, "mirror_radius", 0));
        float mirrorChance = clamp01(GsonHelper.getAsFloat(json, "mirror_chance", 0.0F));

        List<JsonObject> rawTriggers = new ArrayList<>();
        if (json.has("triggers") && json.get("triggers").isJsonArray()) {
            for (JsonElement e : json.getAsJsonArray("triggers")) {
                if (e.isJsonObject()) rawTriggers.add(e.getAsJsonObject());
            }
        }

        List<ReactionBinding> bindings = new ArrayList<>();
        if (json.has("bindings") && json.get("bindings").isJsonArray()) {
            for (JsonElement e : json.getAsJsonArray("bindings")) {
                ReactionBinding b = ReactionBinding.parse(e);
                if (b != null) bindings.add(b);
            }
        }
        if (bindings.isEmpty()) {
            Townstead.LOGGER.warn("Reaction '{}' has no usable bindings; will never fire", id);
        }
        return new Reaction(id, displayName, tags, cooldownTicks, chance, lockTicks,
                conditions, mirrorRadius, mirrorChance,
                Collections.unmodifiableList(rawTriggers),
                Collections.unmodifiableList(bindings));
    }

    private static float clamp01(float value) {
        if (value < 0.0F) return 0.0F;
        if (value > 1.0F) return 1.0F;
        return value;
    }
}
