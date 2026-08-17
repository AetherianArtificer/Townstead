package com.aetherianartificer.townstead.compat.thirst;

import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.compat.ModCompat;

import javax.annotation.Nullable;

public final class ThirstBridgeResolver {
    private static volatile String resolvedPreference;
    private static @Nullable ThirstCompatBridge cachedBridge;
    private static @Nullable ThirstCompatBridge cachedNativeBridge;

    private ThirstBridgeResolver() {}

    /**
     * Lightweight check: is any supported thirst mod present?
     * Safe to call during mod construction / config building (no reflection).
     */
    public static boolean anyThirstModLoaded() {
        return ModCompat.isLoaded("thirst") || ModCompat.isLoaded("legendarysurvivaloverhaul");
    }

    public static @Nullable ThirstCompatBridge get() {
        // Re-resolve when the configured preference changes (server config loads
        // after mod init and can differ per world).
        String preference = TownsteadConfig.preferredThirstBackend();
        if (!preference.equals(resolvedPreference)) {
            resolve(preference);
        }
        return cachedBridge;
    }

    public static boolean isActive() {
        return get() != null;
    }

    /** The selected backend without Townstead's datapack overlay. */
    public static @Nullable ThirstCompatBridge getNative() {
        String preference = TownsteadConfig.preferredThirstBackend();
        if (!preference.equals(resolvedPreference)) resolve(preference);
        return cachedNativeBridge;
    }

    private static synchronized void resolve(String preference) {
        if (preference.equals(resolvedPreference)) return;
        // TWR and TWP share the "thirst" mod id, so at most one of them can be
        // installed; TWR is checked first because its presence makes TWP's init
        // fail with a warning.
        ThirstCompatBridge lso = LSOBridge.INSTANCE.isActive() ? LSOBridge.INSTANCE : null;
        ThirstCompatBridge thirst = ThirstWasReclaimedBridge.INSTANCE.isActive()
                ? ThirstWasReclaimedBridge.INSTANCE
                : ThirstWasTakenBridge.INSTANCE.isActive() ? ThirstWasTakenBridge.INSTANCE : null;
        ThirstCompatBridge selected = switch (preference) {
            case "thirst" -> thirst != null ? thirst : lso;
            // "auto" prefers LSO, matching pre-config behavior
            default -> lso != null ? lso : thirst;
        };
        cachedNativeBridge = selected;
        cachedBridge = selected == null ? null : new ConfiguredThirstBridge(selected);
        resolvedPreference = preference;
    }
}
