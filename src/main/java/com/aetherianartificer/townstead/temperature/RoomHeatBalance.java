package com.aetherianartificer.townstead.temperature;

/** Exact solution of C*dT/dt = power + conductance*(outside - T). */
public final class RoomHeatBalance {
    private RoomHeatBalance() {}
    /** Share output among exposed air faces; solid furniture/walls do not consume heater output. */
    public static double sourceShare(int roomFaces, int exposedFaces) {
        if (roomFaces <= 0) return 0;
        return Math.min(1.0, roomFaces / (double) Math.max(1, exposedFaces));
    }
    /** Heat reaches six blocks, preserving exposure within the first block and the stored heat budget. */
    public static double localExposure(double sourceEffect, double distanceSquared) {
        double distance = Math.sqrt(Math.max(0, distanceSquared));
        if (sourceEffect > 0 && distance > 1) {
            distance = 1 + (distance - 1) * 2 / 5;
        }
        return sourceEffect * Math.max(0, 1 - distance / 3.0);
    }
    /** Gameplay tuning: keep cold-room relief, soften extra stove exposure once air is warm. */
    public static double warmExposure(double roomTemperature, double localHeat) {
        double warmRoom = Math.max(0, Math.min(1, (roomTemperature - 20) / 16));
        return localHeat * (1 - 0.75 * warmRoom);
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
