package com.aetherianartificer.townstead.ai.work.producer;

public enum ProducerBlockedReason {
    NONE,
    NO_WORKSITE,
    NO_INGREDIENTS,
    NO_RECIPE,
    NO_STORAGE,
    UNREACHABLE,
    NO_FUEL,
    OUTPUT_BLOCKED,
    UNSUPPORTED_RECIPE
}
