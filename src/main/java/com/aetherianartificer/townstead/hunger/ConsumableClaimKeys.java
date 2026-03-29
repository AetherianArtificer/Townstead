package com.aetherianartificer.townstead.hunger;

final class ConsumableClaimKeys {
    private ConsumableClaimKeys() {}

    static String posClaimKey(String dimensionId, String category, long posAsLong) {
        return normalizeDimension(dimensionId) + "|" + category + "|pos:" + posAsLong;
    }

    static String slotClaimKey(String dimensionId, String category, long posAsLong, int slot, boolean itemHandler, String sideName) {
        String normalizedSide = (sideName == null || sideName.isBlank()) ? "none" : sideName;
        return normalizeDimension(dimensionId) + "|" + category + "|slot:" + posAsLong + ":" + slot + ":" + itemHandler + ":" + normalizedSide;
    }

    private static String normalizeDimension(String dimensionId) {
        if (dimensionId == null || dimensionId.isBlank()) return "unknown";
        return dimensionId;
    }
}
