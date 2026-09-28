package com.aetherianartificer.townstead.switchboard;

import com.aetherianartificer.townstead.compat.ModCompat;

import java.util.Map;

/**
 * Setting sections that belong to another mod's compat: shown on the Switchboard only when that
 * mod is installed, so a world without it never sees settings that can do nothing.
 */
public final class CompatSections {
    private static final Map<String, String> SECTION_MOD = Map.of("vampirism", "vampirism");

    private CompatSections() {}

    /** Whether the setting at {@code key} (a dotted config path) has its mod present. */
    public static boolean present(String key) {
        int dot = key.indexOf('.');
        String mod = SECTION_MOD.get(dot < 0 ? key : key.substring(0, dot));
        return mod == null || ModCompat.isLoaded(mod);
    }
}
