package com.aetherianartificer.townstead.social;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Read-only experience. Live readers and offline subjects share this contract; it owns no storage. */
public interface SocialKnowledge {
    long today();
    List<Memory> memories();
    double sentiment(UUID toward);

    /** Data-defined directional quality. Existing snapshots treat sentiment as affection. */
    default double relationship(UUID toward, String quality) {
        return RelationshipQualities.AFFECTION.equals(quality) ? sentiment(toward) : 0D;
    }

    /** Personal inclination known about the subject; unavailable snapshots return NaN. */
    default double socialInclination(String inclination) { return Double.NaN; }

    record Memory(String key, UUID other, long lastDay, int count, double strength, double valence) {}

    record Snapshot(long today, List<Memory> memories, Map<UUID, Double> sentiments) implements SocialKnowledge {
        public Snapshot { memories = List.copyOf(memories); sentiments = Map.copyOf(sentiments); }
        @Override public double sentiment(UUID toward) { return sentiments.getOrDefault(toward, 0D); }
    }
}
