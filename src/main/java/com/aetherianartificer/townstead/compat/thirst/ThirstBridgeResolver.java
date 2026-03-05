package com.aetherianartificer.townstead.compat.thirst;

import com.aetherianartificer.townstead.compat.ModCompat;

import javax.annotation.Nullable;

public final class ThirstBridgeResolver {
    private static volatile boolean resolved;
    private static @Nullable ThirstCompatBridge cachedBridge;

    private ThirstBridgeResolver() {}

    /**
     * Lightweight check: is any supported thirst mod present?
     * Safe to call during mod construction / config building (no reflection).
     */
    public static boolean anyThirstModLoaded() {
        return ModCompat.isLoaded("thirst") || ModCompat.isLoaded("legendarysurvivaloverhaul");
    }

    public static @Nullable ThirstCompatBridge get() {
        if (!resolved) {
            resolve();
        }
        return cachedBridge;
    }

    public static boolean isActive() {
        return get() != null;
    }

    private static synchronized void resolve() {
        if (resolved) return;
        // Priority: LSO > TWP
        if (LSOBridge.INSTANCE.isActive()) {
            cachedBridge = LSOBridge.INSTANCE;
        } else if (ThirstWasTakenBridge.INSTANCE.isActive()) {
            cachedBridge = ThirstWasTakenBridge.INSTANCE;
        } else {
            cachedBridge = null;
        }
        resolved = true;
    }
}
