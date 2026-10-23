package com.aetherianartificer.townstead.compat.cozycafe;

import com.aetherianartificer.townstead.hospitality.service.HospitalityServiceProviders;

/** Optional Cozy Cafe compatibility entry point; contains no Cozy compile-time references. */
public final class CozyCafeCompat {
    private CozyCafeCompat() {}

    public static void bootstrap() {
        HospitalityServiceProviders.register(new CozyCafeServiceProvider());
    }
}
