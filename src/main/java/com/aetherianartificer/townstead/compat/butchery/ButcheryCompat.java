package com.aetherianartificer.townstead.compat.butchery;

import com.aetherianartificer.townstead.compat.ModCompat;

/**
 * Entry point for the optional Butchery mod integration. See
 * {@code docs/design/butchery_integration.md} for the full design.
 *
 * v1 scaffolding only: mod-loaded detection plus the shared constants the rest
 * of the integration will reference. Building-type JSON is served through
 * {@link com.aetherianartificer.townstead.compat.ConditionalCompatPack}; no
 * programmatic registration is needed here yet.
 */
public final class ButcheryCompat {
    public static final String MOD_ID = "butchery";

    private ButcheryCompat() {}

    public static boolean isLoaded() {
        return ModCompat.isLoaded(MOD_ID);
    }
}
