package com.aetherianartificer.townstead.temperature;

/** Diagnostics occupy unused flag bits, preserving the existing packet layout and tier bits. */
public final class ThermalStatus {
    private static final String[] REASONS = {"none", "dress_fetch", "dress_stow", "wardrobe_checked",
            "no_wearable_protection", "thermal_provision", "recover_here", "home", "outside",
            "dry_shelter", "shade", "hearth", "cool_spot", "hearth_set", "cool_set", "heat_source",
            "cooling_source", "dry_land", "safer_refuge", "no_effective_relief", "recovered"};
    private ThermalStatus() {}
    public static int flags(float body, float target, String reason) {
        int trend = target > body + .05f ? 1 : target < body - .05f ? 2 : 0;
        int code = 0;
        for (int i = 0; i < REASONS.length; i++) if (REASONS[i].equals(reason)) { code = i; break; }
        return (trend << 8) | (code << 10);
    }
    public static String trend(int flags) { return switch ((flags >> 8) & 3) {
        case 1 -> "warming"; case 2 -> "cooling"; default -> "steady";
    }; }
    public static String reason(int flags) {
        int code = (flags >> 10) & 63;
        return code < REASONS.length ? REASONS[code] : "none";
    }
}
