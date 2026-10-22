package com.aetherianartificer.townstead.temperature;

/** Flat body offset and directional ambient resistance are different quantities. */
public record ThermalProtection(float offset, float coldResistance, float heatResistance, float thermalResistance) {
    public static final ThermalProtection NONE = new ThermalProtection(0, 0, 0, 0);

    /**
     * Clo per point of resistance. Cold Sweat's goat fur set (46 units) comes to about 3.8 clo and
     * LSO's winter set to about 2.4: the insulation of an arctic outfit.
     */
    public static final float CLO_PER_RESISTANCE = 0.25f;
    /** Resistance at which an effect is an immunity rather than a garment. */
    public static final float IMMUNE = 1000f;

    public ThermalProtection plus(ThermalProtection other) {
        return new ThermalProtection(offset + other.offset, coldResistance + other.coldResistance,
                heatResistance + other.heatResistance, thermalResistance + other.thermalResistance);
    }

    public ThermalProtection minus(ThermalProtection other) {
        return new ThermalProtection(offset - other.offset, coldResistance - other.coldResistance,
                heatResistance - other.heatResistance, thermalResistance - other.thermalResistance);
    }

    /** Insulation against cold, in clo. */
    public float coldClo() {
        return Math.max(0, coldResistance + thermalResistance) * CLO_PER_RESISTANCE;
    }

    /** Shade and insulation against heat, in clo. */
    public float heatClo() {
        return Math.max(0, heatResistance + thermalResistance) * CLO_PER_RESISTANCE;
    }

    /**
     * Clothing lies in series with the body, so it moves ambient toward neutral by a share of the
     * gap, never past it. The body's own heat and exertion are in {@link ThermalComfort#feelsLike}.
     */
    public float protectAmbient(float ambient, float neutral) {
        float gap = ambient - neutral;
        if (gap == 0) return ambient;
        return neutral + gap / (1 + (gap < 0 ? coldClo() : heatClo()));
    }
}
