package com.aetherianartificer.townstead.compat.farmersdelight;

final class CookClaimKeys {
    private CookClaimKeys() {}

    static String claimKey(String dimensionId, long posAsLong) {
        if (dimensionId == null || dimensionId.isBlank()) return "unknown|" + posAsLong;
        return dimensionId + "|" + posAsLong;
    }
}
