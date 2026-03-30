package com.aetherianartificer.townstead.emote;

import net.conczin.mca.entity.ai.relationship.Personality;
import net.minecraft.util.RandomSource;

import java.util.List;

final class EmoteReactionSelector {
    private EmoteReactionSelector() {}

    static EmoteReactionCandidate pick(List<EmoteReactionCandidate> candidates, Personality personality, RandomSource random) {
        if (candidates == null || candidates.isEmpty()) return null;
        int totalWeight = 0;
        for (EmoteReactionCandidate candidate : candidates) {
            totalWeight += Math.max(0, candidate.weightFor(personality));
        }
        if (totalWeight <= 0) return candidates.get(0);
        int roll = random.nextInt(totalWeight);
        for (EmoteReactionCandidate candidate : candidates) {
            roll -= Math.max(0, candidate.weightFor(personality));
            if (roll < 0) return candidate;
        }
        return candidates.get(candidates.size() - 1);
    }
}
