package com.aetherianartificer.townstead.api.v1.model;

/**
 * A village-level reading of one need, from the mean position of residents on that need's
 * scale: {@code THRIVING} at three quarters or better, {@code STEADY} at half, {@code STRAINED}
 * at a quarter, {@code CRISIS} below that. Fixed by Townstead so every consumer sees the same
 * edges.
 */
public enum NeedBand {
    THRIVING("thriving", 0.75),
    STEADY("steady", 0.50),
    STRAINED("strained", 0.25),
    CRISIS("crisis", 0.0);

    private final String id;
    private final double floor;

    NeedBand(String id, double floor) {
        this.id = id;
        this.floor = floor;
    }

    public String id() {
        return id;
    }

    /** The lowest mean fraction of the scale that still counts as this band. */
    public double floor() {
        return floor;
    }

    public static NeedBand of(double meanFraction) {
        for (NeedBand band : values()) {
            if (meanFraction >= band.floor) return band;
        }
        return CRISIS;
    }

    public static NeedBand byId(String id) {
        for (NeedBand band : values()) {
            if (band.id.equals(id)) return band;
        }
        return CRISIS;
    }
}
