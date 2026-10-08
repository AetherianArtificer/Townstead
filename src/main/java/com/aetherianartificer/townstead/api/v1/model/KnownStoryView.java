package com.aetherianartificer.townstead.api.v1.model;

/**
 * A story one knower has heard. {@code fidelity} is 1 for first-hand knowledge and falls with
 * each retelling; {@code channel} names how it arrived ({@code witness}, {@code gossip},
 * {@code village_digest}, {@code player_word}, or a registered channel).
 */
public record KnownStoryView(
        long eventId,
        long accountId,
        float fidelity,
        long learnedDay,
        String channel,
        String templateId,
        long eventDay,
        int villageId,
        float magnitude,
        String reach
) {
}
