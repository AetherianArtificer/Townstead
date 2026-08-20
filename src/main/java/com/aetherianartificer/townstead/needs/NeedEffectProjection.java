package com.aetherianartificer.townstead.needs;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

/**
 * Conservative, planner-readable projection of constant need effects in a Pheno action.
 * Runtime still executes the complete action. Only unconditional arrays / pheno:and nodes are
 * projected, so conditional or dynamic effects never cause the AI to promise benefits it may not get.
 */
public record NeedEffectProjection(int immediateHydration, int lastingHydration, int energy) {
    public static final NeedEffectProjection NONE = new NeedEffectProjection(0, 0, 0);

    public boolean hydrates() { return immediateHydration > 0 || lastingHydration > 0; }
    public boolean energizes() { return energy > 0; }
    public int hydrationScore() { return lastingHydration * 100 + immediateHydration * 10; }

    public NeedEffectProjection plus(NeedEffectProjection other) {
        return new NeedEffectProjection(immediateHydration + other.immediateHydration,
                lastingHydration + other.lastingHydration, energy + other.energy);
    }

    public static NeedEffectProjection project(JsonElement element) {
        if (element == null || element.isJsonNull()) return NONE;
        if (element.isJsonArray()) {
            NeedEffectProjection total = NONE;
            for (JsonElement child : element.getAsJsonArray()) total = total.plus(project(child));
            return total;
        }
        if (!element.isJsonObject()) return NONE;
        JsonObject json = element.getAsJsonObject();
        String type = GsonHelper.getAsString(json, "type", "");
        if ("pheno:hydrate".equals(type)) {
            return new NeedEffectProjection(constant(json.get("immediate")),
                    constant(json.get("lasting")), 0);
        }
        if ("pheno:energize".equals(type)) {
            return new NeedEffectProjection(0, 0, constant(json.get("amount")));
        }
        if ("pheno:and".equals(type)) return project(json.get("actions"));
        return NONE;
    }

    private static int constant(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) return 0;
        return Math.max(0, (int) Math.round(value.getAsDouble()));
    }
}
