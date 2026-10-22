package com.aetherianartificer.townstead.climate;

/** Lets tests in other packages compare against the model's package-private curves. */
public final class ClimateModelTestAccess {
    private ClimateModelTestAccess() {}

    public static float seasonal(float swing, float peakLag, double phase) {
        return ClimateModel.seasonal(swing, peakLag, phase);
    }
}
