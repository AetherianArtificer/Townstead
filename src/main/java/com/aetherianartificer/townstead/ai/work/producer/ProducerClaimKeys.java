package com.aetherianartificer.townstead.ai.work.producer;

final class ProducerClaimKeys {
    private ProducerClaimKeys() {}

    static String claimKey(String dimensionId, long posAsLong) {
        if (dimensionId == null || dimensionId.isBlank()) return "unknown|" + posAsLong;
        return dimensionId + "|" + posAsLong;
    }
}
