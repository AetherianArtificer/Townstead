package com.aetherianartificer.townstead.temperature;

import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * A {@code thermal} declaration: what something warms or cools around it. Object sets carry one
 * (a hearth, a cool spot); a building type may carry one too for a room that is cool by nature,
 * like a cask cellar, in which case only the offset applies to that room.
 * <pre>{ "thermal": { "kind": "warming", "offset": 10, "radius": 6 } }</pre>
 */
public final class ThermalStructures {
    private static volatile Map<String, Spec> BUILDING_SPECS = Map.of();

    private ThermalStructures() {}

    /** @param offset degrees at the centre, positive warms; falls off linearly to zero at {@code radius}. */
    public record Spec(String owner, boolean warming, float offset, int radius) {
        public boolean matches(boolean wantWarm) {
            return warming == wantWarm;
        }
    }

    public static @Nullable Spec parse(String owner, JsonObject json) {
        String kind = GsonHelper.getAsString(json, "kind", "");
        boolean warming = "warming".equalsIgnoreCase(kind) || "heating".equalsIgnoreCase(kind);
        boolean cooling = "cooling".equalsIgnoreCase(kind);
        if (!warming && !cooling) return null;
        float offset = Math.abs(GsonHelper.getAsFloat(json, "offset", warming ? 10f : 8f));
        int radius = Math.max(1, Math.min(32, GsonHelper.getAsInt(json, "radius", 6)));
        return new Spec(owner, warming, warming ? offset : -offset, radius);
    }

    /** Building-type declarations from the extended-building files. */
    public static void replaceAll(Map<String, Spec> specs) {
        BUILDING_SPECS = Map.copyOf(specs);
    }

    public static @Nullable Spec spec(String buildingType) {
        return buildingType == null ? null : BUILDING_SPECS.get(buildingType);
    }

    public static Map<String, Spec> newIndex() {
        return new HashMap<>();
    }
}
