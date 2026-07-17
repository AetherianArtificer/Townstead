package com.aetherianartificer.townstead.chronicle.model;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * One knower's belief about a story. {@code storyEventId} is nullable-by-value
 * ({@link ChronicleEvent#NONE}): a fabricated account has no backing event and
 * carries the whole claim in its overlay. {@code sourceAccountId} links the
 * gossip chain (who told whom); {@code overlayJson} is the accumulated
 * distortion delta over the truth.
 */
public record Account(
        long accountId,
        long storyEventId,
        UUID knower,
        String channel,
        long sourceAccountId,
        float fidelity,
        long learnedDay,
        @Nullable String overlayJson) {

    public Account {
        Objects.requireNonNull(knower, "knower");
        Objects.requireNonNull(channel, "channel");
    }
}
