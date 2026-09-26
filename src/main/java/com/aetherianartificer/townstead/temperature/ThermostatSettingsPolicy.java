package com.aetherianartificer.townstead.temperature;

/** Packet validation kept independent of rendering and the temperature simulation. */
public final class ThermostatSettingsPolicy {
    private ThermostatSettingsPolicy() {}
    public static boolean valid(int mode,int target) { return mode>=0 && mode<3 && target>=5 && target<=35; }
}
