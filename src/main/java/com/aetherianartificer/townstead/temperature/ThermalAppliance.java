package com.aetherianartificer.townstead.temperature;

import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

/**
 * A heat or cooling source from physical properties: the power it puts into its room, and the
 * temperature and size of its hot or cold face, which set the radiant heat a body feels nearby.
 * {@code radiant_degrees}, when set, overrides the radiant strength. A drafting source burns room
 * air, so its heat also pulls outside air through the room. A reversible appliance heats or cools
 * as the sign of its native effect says, like a hearth with a heating and a cooling side.
 */
public record ThermalAppliance(double watts, float radiantDegrees, float nativeReference, boolean draft, boolean reversible,
                               float surfaceCelsius, float coldSurfaceCelsius, float size) {
    /** Air temperature assumed when a radiant strength is asked for without one. */
    public static final float REFERENCE_AIR = 15f;

    public ThermalAppliance {
        if (!Double.isFinite(watts) || !Float.isFinite(nativeReference) || !Float.isFinite(size) || size < 0
                || Math.abs(watts) > 1000000 || Float.isFinite(radiantDegrees) && Math.abs(radiantDegrees) > 1000
                || Float.isFinite(surfaceCelsius) && Math.abs(surfaceCelsius) > 5000
                || Float.isFinite(coldSurfaceCelsius) && Math.abs(coldSurfaceCelsius) > 5000
                || Float.isInfinite(radiantDegrees) || Float.isInfinite(surfaceCelsius) || Float.isInfinite(coldSurfaceCelsius))
            throw new IllegalArgumentException("Invalid thermal appliance profile");
    }
    public ThermalAppliance(double watts, float radiantDegrees, float nativeReference, boolean draft, boolean reversible) {
        this(watts, radiantDegrees, nativeReference, draft, reversible, Float.NaN, Float.NaN, 1f);
    }
    public ThermalAppliance(double watts, float radiantDegrees, float nativeReference, boolean draft) {
        this(watts, radiantDegrees, nativeReference, draft, false);
    }
    public ThermalAppliance(double watts, float radiantDegrees, float nativeReference) {
        this(watts, radiantDegrees, nativeReference, true);
    }

    /** Radiant strength at the source's face in air at {@code air} degrees: the override, else from its surface. */
    public float radiantStrength(float air) {
        if (Float.isFinite(radiantDegrees)) return radiantDegrees;
        return surfaceStrength(surfaceCelsius, air);
    }

    private float surfaceStrength(float surface, float air) {
        if (!Float.isFinite(surface)) return 0f;
        return TemperatureSettings.get().radiantPerSurfaceDegree() * size * (surface - air);
    }

    public record Output(double watts, float radiantDegrees, boolean estimated) {}
    public Output output(float nativeEffect, boolean active) {
        return output(nativeEffect, active, REFERENCE_AIR);
    }
    public Output output(float nativeEffect, boolean active, float air) {
        // Native zero is authoritative, including a machine that has power but no fuel.
        if (!active || nativeEffect == 0) return new Output(0, 0, false);
        double fraction = Float.isFinite(nativeEffect) && nativeReference != 0
                ? (reversible ? nativeEffect / nativeReference : Math.max(0, nativeEffect / nativeReference)) : 1;
        // A reversible source's cooling side radiates from its own cold face.
        if (fraction < 0 && !Float.isFinite(radiantDegrees) && Float.isFinite(coldSurfaceCelsius))
            return new Output(watts * fraction, (float) (surfaceStrength(coldSurfaceCelsius, air) * -fraction), false);
        return new Output(watts * fraction, (float) (radiantStrength(air) * fraction), false);
    }
    public static ThermalAppliance parse(JsonObject json) {
        double watts = json.has("power_w") ? GsonHelper.getAsDouble(json, "power_w") : GsonHelper.getAsDouble(json, "room_power_w");
        return new ThermalAppliance(watts,
                GsonHelper.getAsFloat(json, "radiant_degrees", Float.NaN),
                GsonHelper.getAsFloat(json, "native_reference_degrees", 0),
                GsonHelper.getAsBoolean(json, "draft", true),
                GsonHelper.getAsBoolean(json, "reversible", false),
                GsonHelper.getAsFloat(json, "surface_c", Float.NaN),
                GsonHelper.getAsFloat(json, "cold_surface_c", Float.NaN),
                Math.max(0f, GsonHelper.getAsFloat(json, "size", 1f)));
    }
}
