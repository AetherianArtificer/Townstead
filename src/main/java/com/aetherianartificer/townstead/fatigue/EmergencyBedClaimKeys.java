package com.aetherianartificer.townstead.fatigue;

final class EmergencyBedClaimKeys {
    private EmergencyBedClaimKeys() {}

    static String claimKey(String dimensionId, long posAsLong) {
        if (dimensionId == null || dimensionId.isBlank()) {
            return "unknown|" + posAsLong;
        }
        return dimensionId + "|" + posAsLong;
    }
}
