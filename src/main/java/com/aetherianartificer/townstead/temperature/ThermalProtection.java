package com.aetherianartificer.townstead.temperature;

/** Flat body offset and directional ambient resistance are different quantities. */
public record ThermalProtection(float offset, float coldResistance, float heatResistance, float thermalResistance) {
    public static final ThermalProtection NONE = new ThermalProtection(0, 0, 0, 0);

    public ThermalProtection plus(ThermalProtection other) {
        return new ThermalProtection(offset + other.offset, coldResistance + other.coldResistance,
                heatResistance + other.heatResistance, thermalResistance + other.thermalResistance);
    }

    /** Resistance moves ambient toward neutral, and cannot push it past neutral. */
    public float protectAmbient(float ambient, float neutral) {
        float gap = ambient - neutral;
        float resistance = Math.max(0, thermalResistance + (gap < 0 ? coldResistance : heatResistance));
        return ambient - Math.signum(gap) * Math.min(Math.abs(gap), resistance);
    }
}
