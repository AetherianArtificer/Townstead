package com.aetherianartificer.townstead.needs;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

/**
 * Conservative, planner-readable projection of constant need effects in a Pheno action.
 * Runtime still executes the complete action. Only unconditional arrays / pheno:and nodes are
 * projected, so conditional or dynamic effects never cause the AI to promise benefits it may not get.
 */
public record NeedEffectProjection(int immediateHydration, int lastingHydration, int energy, int warmthTenths,
                                   int influenceTenths, int influenceTicks) {
    public static final NeedEffectProjection NONE = new NeedEffectProjection(0, 0, 0, 0);

    public NeedEffectProjection(int immediateHydration, int lastingHydration, int energy) {
        this(immediateHydration, lastingHydration, energy, 0);
    }

    public NeedEffectProjection(int immediateHydration, int lastingHydration, int energy, int warmthTenths) {
        this(immediateHydration, lastingHydration, energy, warmthTenths, 0, 0);
    }
    public boolean thermal() { return warmthTenths != 0 || influenceTenths != 0 || influenceTicks > 0; }

    /** Body-temperature change in Celsius tenths: positive warms, negative cools. */
    public boolean warms() { return warmthTenths > 0 || influenceTenths > 0; }
    public boolean cools() { return warmthTenths < 0 || influenceTenths < 0; }

    public boolean hydrates() { return immediateHydration > 0 || lastingHydration > 0; }
    public boolean energizes() { return energy > 0; }
    public int hydrationScore() { return lastingHydration * 100 + immediateHydration * 10; }

    public NeedEffectProjection plus(NeedEffectProjection other) {
        return new NeedEffectProjection(immediateHydration + other.immediateHydration,
                lastingHydration + other.lastingHydration, energy + other.energy, warmthTenths + other.warmthTenths,
                other.influenceTicks == 0 ? influenceTenths : influenceTicks == 0 ? other.influenceTenths
                        : Integer.signum(influenceTenths) == Integer.signum(other.influenceTenths) ? other.influenceTenths : 0,
                other.influenceTicks == 0 ? influenceTicks : influenceTicks == 0 ? other.influenceTicks
                        : Integer.signum(influenceTenths) == Integer.signum(other.influenceTenths) ? other.influenceTicks
                        : Math.min(influenceTicks, other.influenceTicks));
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
        if ("pheno:warm".equals(type) || "pheno:cool".equals(type)) {
            int amount = tenths(json.get("amount")) * ("pheno:warm".equals(type) ? 1 : -1);
            int duration = Math.max(0, constant(json.get("duration")));
            return duration == 0 ? new NeedEffectProjection(0, 0, 0, amount)
                    : new NeedEffectProjection(0, 0, 0, 0, amount, duration);
        }
        if ("pheno:and".equals(type)) return project(json.get("actions"));
        return NONE;
    }

    private static int tenths(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) return 0;
        double amount = value.getAsDouble();
        return !Double.isFinite(amount) ? 0 : (int) Math.max(0, Math.min(Integer.MAX_VALUE, Math.round(amount * 10.0)));
    }

    private static int constant(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) return 0;
        double amount = value.getAsDouble();
        return !Double.isFinite(amount) ? 0 : (int) Math.max(0, Math.min(Integer.MAX_VALUE, Math.round(amount)));
    }
}
