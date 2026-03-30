package com.aetherianartificer.townstead.emote;

import net.conczin.mca.entity.ai.relationship.Personality;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public record EmoteReactionCandidate(
        String villagerEmote,
        String chatKeyPrefix,
        int chatVariants,
        double chatChance,
        int weight,
        Map<Personality, Integer> personalityWeights
) {
    public EmoteReactionCandidate {
        personalityWeights = personalityWeights == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new EnumMap<>(personalityWeights));
    }

    public int weightFor(Personality personality) {
        if (personality == null) return weight;
        return personalityWeights.getOrDefault(personality, weight);
    }
}
