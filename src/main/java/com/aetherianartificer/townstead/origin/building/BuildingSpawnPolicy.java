package com.aetherianartificer.townstead.origin.building;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A spawner building's origin policy: which origins it may spawn ({@code allowedOrigins}; empty means
 * "any"), which it must not ({@code deniedOrigins}), and whether the village-majority disposition
 * filter still applies inside it ({@code checkDispositions}). Authored under
 * {@code data/<ns>/building_spawn/<building_type>.json}, keyed by MCA building-type id (e.g. {@code inn}).
 * Honored today for MCA's inn spawns; forward-compatible for any future building-driven spawn.
 */
public record BuildingSpawnPolicy(Set<String> allowedOrigins, Set<String> deniedOrigins, boolean checkDispositions) {

    /** Whether this building may spawn the given origin id (deny wins; an allow list restricts). */
    public boolean allows(String originId) {
        if (originId == null) return false;
        if (deniedOrigins.contains(originId)) return false;
        return allowedOrigins.isEmpty() || allowedOrigins.contains(originId);
    }

    /**
     * Parse a spawn-policy object: {@code allowed_origins} / {@code denied_origins} (origin id lists,
     * normalized to canonical strings) and {@code check_village_dispositions} (default true). Shared by
     * the canonical {@code extended_buildings} loader and the legacy {@code building_spawn/} reader.
     */
    public static BuildingSpawnPolicy parse(JsonObject obj) {
        boolean check = GsonHelper.getAsBoolean(obj, "check_village_dispositions", true);
        return new BuildingSpawnPolicy(originIds(obj, "allowed_origins"), originIds(obj, "denied_origins"), check);
    }

    private static Set<String> originIds(JsonObject obj, String key) {
        Set<String> out = new LinkedHashSet<>();
        if (obj.has(key) && obj.get(key).isJsonArray()) {
            for (JsonElement e : obj.getAsJsonArray(key)) {
                if (!e.isJsonPrimitive()) continue;
                ResourceLocation id = DataPackLang.parseId(e.getAsString());
                if (id != null) out.add(id.toString());
            }
        }
        return out;
    }
}
