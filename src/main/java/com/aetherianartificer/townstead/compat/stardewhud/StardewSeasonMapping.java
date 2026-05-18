package com.aetherianartificer.townstead.compat.stardewhud;

/**
 * Maps Townstead's season translation key back to Stardew HUD's hardcoded
 * season index (0=spring, 1=summer, 2=autumn, 3=winter). Lives here rather
 * than on {@code Season} itself so the core enum has no knowledge of any
 * particular compat target.
 *
 * Used by {@code StardewHudSeasonMixin} to redirect the icon when a seasonal
 * mod is driving Townstead's calendar.
 */
public final class StardewSeasonMapping {
    private StardewSeasonMapping() {}

    /** -1 if the key is null/empty or doesn't match any known season suffix. */
    public static int indexFromKey(String key) {
        if (key == null || key.isEmpty()) return -1;
        if (key.endsWith(".spring")) return 0;
        if (key.endsWith(".summer")) return 1;
        if (key.endsWith(".autumn") || key.endsWith(".fall")) return 2;
        if (key.endsWith(".winter")) return 3;
        return -1;
    }
}
