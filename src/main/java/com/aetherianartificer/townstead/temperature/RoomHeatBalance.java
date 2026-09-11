package com.aetherianartificer.townstead.temperature;

/** Exact solution of C*dT/dt = power + conductance*(outside - T). */
public final class RoomHeatBalance {
    private RoomHeatBalance() {}
    /** Share output among exposed air faces; solid furniture/walls do not consume heater output. */
    public static double sourceShare(int roomFaces, int exposedFaces) {
        if (roomFaces <= 0) return 0;
        return Math.min(1.0, roomFaces / (double) Math.max(1, exposedFaces));
    }
    /** Visible-source exposure, smoothly cut off at six blocks; this is not stored heat. */
    public static double localExposure(double sourceEffect, double distanceSquared) {
        double d2 = Math.max(0, distanceSquared);
        return sourceEffect * Math.max(0, (1 / (1 + d2) - 1.0 / 37) / (1 - 1.0 / 37));
    }

    /** Background ventilation: air volume heat capacity is approximately 1200 J/m3/K. */
    public static double ventilation(double volume, double airChangesPerHour) {
        return volume * 1200 * airChangesPerHour / 3600;
    }

    /** Mod temperature contributions are authored strengths, converted once to effective watts. */
    public static double sourcePower(double nativeDegrees, double wattsPerDegree, int faces, int exposedFaces) {
        return nativeDegrees * wattsPerDegree * sourceShare(faces, exposedFaces);
    }
    /** Cooking strength describes local warmth, not the output of a dedicated space heater. */
    public static double roomSourcePower(double nativeDegrees, double wattsPerDegree, int faces, int exposedFaces,
                                         boolean cooking, double cookingFraction) {
        return sourcePower(nativeDegrees, wattsPerDegree, faces, exposedFaces)
                * (cooking && nativeDegrees > 0 ? cookingFraction : 1);
    }
    public static double advance(double temperature, double capacity, double power,
                                 double conductance, double reservoir, double seconds) {
        if (!Double.isFinite(temperature) || !Double.isFinite(reservoir)
                || !Double.isFinite(power) || !Double.isFinite(capacity)
                || !Double.isFinite(conductance) || !Double.isFinite(seconds)
                || capacity <= 0 || conductance < 0 || seconds < 0)
            throw new IllegalArgumentException("Invalid room heat balance");
        if (conductance == 0) return temperature + power * seconds / capacity;
        double equilibrium = reservoir + power / conductance;
        return temperature + (equilibrium - temperature) * -Math.expm1(-conductance * seconds / capacity);
    }
}
