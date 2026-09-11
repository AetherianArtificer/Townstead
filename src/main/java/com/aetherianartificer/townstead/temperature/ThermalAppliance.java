package com.aetherianartificer.townstead.temperature;

import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

/** Authored gameplay output, independent of native ambient influence units. */
public record ThermalAppliance(double watts, float radiantDegrees, float nativeReference) {
    public ThermalAppliance {
        if (!Double.isFinite(watts) || !Float.isFinite(radiantDegrees) || !Float.isFinite(nativeReference)
                || Math.abs(watts) > 1000000 || Math.abs(radiantDegrees) > 1000)
            throw new IllegalArgumentException("Invalid thermal appliance profile");
    }
    public record Output(double watts, float radiantDegrees, boolean estimated) {}
    public Output output(float nativeEffect, boolean active) {
        // Native zero is authoritative, including a machine that has power but no fuel.
        if (!active || nativeEffect == 0) return new Output(0, 0, false);
        double fraction = Float.isFinite(nativeEffect) && nativeReference != 0
                ? Math.max(0, nativeEffect / nativeReference) : 1;
        return new Output(watts * fraction, (float) (radiantDegrees * fraction), false);
    }
    public static ThermalAppliance parse(JsonObject json) {
        return new ThermalAppliance(GsonHelper.getAsDouble(json, "room_power_w"),
                GsonHelper.getAsFloat(json, "radiant_degrees", 0),
                GsonHelper.getAsFloat(json, "native_reference_degrees", 0));
    }
}
