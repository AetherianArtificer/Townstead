package com.aetherianartificer.townstead.temperature;

/** Exact solution of C*dT/dt = power + conductance*(outside - T). */
public final class RoomHeatBalance {
    private RoomHeatBalance() {}
    /** Share output among exposed air faces; solid furniture/walls do not consume heater output. */
    public static double sourceShare(int roomFaces, int exposedFaces) {
        if (roomFaces <= 0) return 0;
        return Math.min(1.0, roomFaces / (double) Math.max(1, exposedFaces));
    }
    /** Visible-source exposure, falling linearly to nothing at six blocks; this is not stored heat. */
    public static double localExposure(double sourceEffect, double distanceSquared) {
        double distance = Math.sqrt(Math.max(0, distanceSquared));
        if (distance > 1) distance = 1 + (distance - 1) * 2 / 5;
        return sourceEffect * Math.max(0, 1 - distance / 3.0);
    }

    /** The strongest visible source dominates; overlapping sources share a finite field of view. */
    public static double combinedExposure(double total, double strongest) {
        if (total <= 0 || strongest <= 0) return 0;
        double extra = Math.max(0, total - strongest);
        return strongest + strongest * .25 * -Math.expm1(-extra / strongest);
    }

    /** Outside air a burning source pulls through the room, in W/K. */
    public static double draft(double watts, double maxRise) {
        return watts > 0 && maxRise > 0 ? watts / maxRise : 0;
    }

    /** Cooling exchanges heat with a finite cold reservoir; it cannot remove energy forever. */
    public record Cooling(double conductance, double reservoir) {}
    public static Cooling cooling(double watts, double ambient, double maxDrop) {
        double drop = Math.max(1, Math.abs(maxDrop));
        return new Cooling(Math.max(0, -watts) / drop, ambient - drop);
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
