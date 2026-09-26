package com.aetherianartificer.townstead.temperature;

/** Time spent continuously in Cold/Hot, independent of earlier mild environmental strain. */
public final class ThermalBreakGrace {
    public static final double SECONDS = 60;
    private TemperatureData.Tier previous = TemperatureData.Tier.COMFORTABLE;
    private double elapsed;

    public void update(TemperatureData.Tier core, double seconds) {
        if (core.severity() != 2 || core != previous) elapsed = 0;
        else elapsed = Math.min(SECONDS, elapsed + Math.max(0, seconds));
        previous = core;
    }

    public boolean allowsBreak(TemperatureData.Tier core) {
        return core.isCrisis() || core.severity() == 2 && core == previous && elapsed >= SECONDS;
    }
}
