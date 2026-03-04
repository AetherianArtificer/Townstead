package com.aetherianartificer.townstead.compat.thirst;

import javax.annotation.Nullable;

public final class ThirstBridgeResolver {
    private static volatile boolean resolved;
    private static @Nullable ThirstCompatBridge cachedBridge;

    private ThirstBridgeResolver() {}

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
