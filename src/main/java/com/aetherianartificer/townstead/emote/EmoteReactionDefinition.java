package com.aetherianartificer.townstead.emote;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public record EmoteReactionDefinition(
        String id,
        Set<String> triggerEmotes,
        String requiredMod,
        int radius,
        int cooldownTicks,
        List<EmoteReactionCandidate> candidates
) {
    public EmoteReactionDefinition {
        triggerEmotes = Set.copyOf(new LinkedHashSet<>(triggerEmotes));
        candidates = List.copyOf(candidates);
    }
}
