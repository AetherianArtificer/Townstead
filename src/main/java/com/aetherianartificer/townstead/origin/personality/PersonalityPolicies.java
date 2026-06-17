package com.aetherianartificer.townstead.origin.personality;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses the optional {@code personalities} block shared by the four origin loaders. Tolerant: a
 * malformed entry is skipped, an absent block yields {@link Personalities#EMPTY}.
 *
 * <pre>
 * "personalities": {
 *   "inherit": false,
 *   "allow": { "townstead_skeleton:cryptic": 3, "odd": 1 },
 *   "deny":  [ "townstead_skeleton:dead_last" ]
 * }
 * </pre>
 */
public final class PersonalityPolicies {

    private PersonalityPolicies() {}

    public static Personalities parse(JsonObject obj) {
        if (!obj.has("personalities") || !obj.get("personalities").isJsonObject()) return Personalities.EMPTY;
        JsonObject p = obj.getAsJsonObject("personalities");

        boolean inherit = GsonHelper.getAsBoolean(p, "inherit", false);

        Map<String, Integer> allow = new LinkedHashMap<>();
        if (p.has("allow") && p.get("allow").isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : p.getAsJsonObject("allow").entrySet()) {
                try {
                    allow.put(e.getKey(), Math.max(0, e.getValue().getAsInt()));
                } catch (RuntimeException ignored) {
                    // non-numeric weight: skip this entry
                }
            }
        }

        List<String> deny = new ArrayList<>();
        if (p.has("deny") && p.get("deny").isJsonArray()) {
            for (JsonElement el : p.getAsJsonArray("deny")) {
                if (el.isJsonPrimitive()) deny.add(el.getAsString());
            }
        }

        return new Personalities(allow, deny, inherit);
    }
}
