package com.aetherianartificer.townstead.temperature;

/** Keeps sub-display precision between updates, while accepting edits to the stored body value. */
public final class BodyTemperatureDrift {
    private double precise = Double.NaN;
    private int lastPublished = Integer.MIN_VALUE;

    public int step(int storedTenths, double target, double rate) {
        if (!Double.isFinite(precise) || storedTenths != lastPublished) precise = storedTenths / 10d;
        precise += (target - precise) * rate;
        precise = Math.max(TemperatureData.MIN_BODY_TENTHS / 10d,
                Math.min(TemperatureData.MAX_BODY_TENTHS / 10d, precise));
        lastPublished = (int) Math.round(precise * 10d);
        return lastPublished;
    }
}
