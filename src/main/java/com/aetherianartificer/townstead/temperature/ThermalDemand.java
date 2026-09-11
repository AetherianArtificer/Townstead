package com.aetherianartificer.townstead.temperature;

/** Control demand only. The caller must operate the actual appliance, not suppress its heat. */
public final class ThermalDemand {
    private ThermalDemand() {}
    public static boolean next(boolean heating, boolean running, double temperature, double target, double halfBand) {
        if (!Double.isFinite(temperature) || !Double.isFinite(target) || !Double.isFinite(halfBand) || halfBand <= 0)
            return false;
        return heating ? (running ? temperature < target + halfBand : temperature < target - halfBand)
                : (running ? temperature > target - halfBand : temperature > target + halfBand);
    }
}
