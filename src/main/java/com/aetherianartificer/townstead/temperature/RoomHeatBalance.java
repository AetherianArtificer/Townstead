package com.aetherianartificer.townstead.temperature;

/** Exact solution of C*dT/dt = power + conductance*(outside - T). */
public final class RoomHeatBalance {
    private RoomHeatBalance() {}
    /** Share output among exposed air faces; solid furniture/walls do not consume heater output. */
    public static double sourceShare(int roomFaces, int exposedFaces) {
        if (roomFaces <= 0) return 0;
        return Math.min(1.0, roomFaces / (double) Math.max(1, exposedFaces));
    }
    /** Radiant falloff scale: a source's strength halves at about 1.3 blocks, then falls as 1/d². */
    public static final double RADIANT_FALLOFF_SQUARED = 5.0 / 3.0;
    /** Radiant heat weaker than this is not felt. */
    public static final double RADIANT_FLOOR = 0.5;

    /** Radiant heat a visible source gives a body at this distance; it warms the body, not the air. */
    public static double localExposure(double sourceEffect, double distanceSquared) {
        double effect = sourceEffect / (1 + Math.max(0, distanceSquared) / RADIANT_FALLOFF_SQUARED);
        return Math.abs(effect) < RADIANT_FLOOR ? 0 : effect;
    }

    /** Cold sources are mostly surfaces that surround the body (snow, ice), so they stack far less. */
    public static final double RADIANT_MAX = 40, RADIANT_STACK = 2, RADIANT_COLD_STACK = 1.25;

    public static double combinedExposure(double total, double strongest) {
        return combinedExposure(total, strongest, RADIANT_MAX, RADIANT_STACK);
    }

    /**
     * Visible sources add almost fully at first, then level off at {@code max} degrees or
     * {@code stack} times the strongest, whichever is lower: three campfires warm far more than
     * one, while a row of weak sources (a kitchen of stoves) stays near its strongest.
     */
    public static double combinedExposure(double total, double strongest, double max, double stack) {
        if (total <= 0 || strongest <= 0) return 0;
        double limit = Math.min(max, strongest * Math.max(1, stack));
        if (limit <= strongest) return strongest;
        double room = limit - strongest;
        double extra = Math.max(0, total - strongest);
        return strongest + room * -Math.expm1(-extra / room);
    }

    /** Watts of upward drive per floor block for each degree between floor and ceiling air. */
    public static final double STRATIFICATION_W_PER_DEGREE = 50;
    public static final double STRATIFICATION_MAX = 8;

    /**
     * Ceiling minus floor air. Warm sources drive it by how far below the ceiling they sit (hot
     * air rises), cold sources by how far above the floor (cold air sinks); both leave the floor
     * coldest. Taller rooms layer more; a room under two blocks does not layer.
     */
    public static double stratification(double warmDrive, double coldDrive, int footprint, int height) {
        if (height < 2) return 0;
        double layered = (warmDrive + coldDrive) / (STRATIFICATION_W_PER_DEGREE * Math.max(1, footprint))
                * Math.min(height, 6) / 3.0;
        return Math.min(STRATIFICATION_MAX, Math.max(0, layered));
    }

    /** Offset from the room's mean at {@code height} blocks above its floor; it averages to zero over the room. */
    public static double layerOffset(double stratification, double heightAboveFloor, int roomHeight) {
        if (roomHeight < 2 || stratification == 0) return 0;
        double z = Math.max(0, Math.min(1, heightAboveFloor / roomHeight));
        return stratification * (z - 0.5);
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
