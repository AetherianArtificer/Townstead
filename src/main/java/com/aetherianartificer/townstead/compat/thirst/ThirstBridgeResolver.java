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
        return ModCompat.isLoaded("thirst") || ModCompat.isLoaded("legendarysurvivaloverhaul")
                || ModCompat.isLoaded("toughasnails");
    }

    public static @Nullable ThirstCompatBridge get() {
        // Re-resolve when the configured preference changes (server config loads
        // after mod init and can differ per world).
        String preference = TownsteadConfig.preferredThirstBackend();
        if (!preference.equals(resolvedPreference)) {
            resolve(preference);
        }
        // Selection is cached; the backend toggle is not (configs can load/sync later).
        // A disabled selected backend must not silently fall back to another mod.
        return cachedBridge != null && cachedBridge.isThirstEnabled() ? cachedBridge : null;
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
        ThirstCompatBridge tan = ToughAsNailsThirstBridge.INSTANCE.isActive() ? ToughAsNailsThirstBridge.INSTANCE : null;
        ThirstCompatBridge fallback = lso != null ? lso : thirst != null ? thirst : tan;
        ThirstCompatBridge selected = switch (preference) {
            case "tough_as_nails" -> tan != null ? tan : fallback;
            case "thirst" -> thirst != null ? thirst : fallback;
            // "auto" prefers LSO, matching pre-config behavior
            default -> fallback;
        };
        cachedNativeBridge = selected;
        cachedBridge = selected == null ? null : new ConfiguredThirstBridge(selected);
        resolvedPreference = preference;
    }
}
