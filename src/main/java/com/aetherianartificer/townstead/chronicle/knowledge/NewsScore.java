package com.aetherianartificer.townstead.chronicle.knowledge;

import com.aetherianartificer.townstead.chronicle.template.ChronicleEventTemplate;

/**
 * How interesting a story is to an audience right now — roughly surprisal:
 * base value × rarity × magnitude × proximity × recency. Novelty is handled
 * structurally (spread only offers stories the audience doesn't know).
 * Drives gossip selection, digest ordering, and news points.
 */
public final class NewsScore {

    private static final float RECENCY_DECAY_PER_DAY = 0.92f;
    private static final float FOREIGN_VILLAGE_FACTOR = 0.6f;

    private NewsScore() {}

    public static float score(ChronicleEventTemplate template, float magnitude,
                              long eventDay, int eventVillageId,
                              long today, int audienceVillageId) {
        // Rarity is frequency, not importance: it decides how often a template is
        // picked, never how big a deal it is once it happens.
        float base = template.newsValue();
        float mag = Math.max(0.2f, magnitude);
        float proximity = (eventVillageId >= 0 && eventVillageId == audienceVillageId)
                ? 1f : FOREIGN_VILLAGE_FACTOR;
        long age = Math.max(0, today - eventDay);
        float recency = (float) Math.pow(RECENCY_DECAY_PER_DAY, Math.min(age, 60));
        return base * mag * proximity * recency;
    }
}
