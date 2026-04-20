package com.aetherianartificer.townstead.spirit;

import java.util.Map;

/**
 * Aggregate spirit points for a single village. Computed fresh from the live
 * building list — never cached inside this record; caching is the caller's
 * responsibility (server savedData, client store, etc.).
 *
 * {@code perSpirit} is immutable and contains only spirit ids that received
 * a non-zero contribution. {@code contributingBuildings} is the count of
 * {@code isComplete()} buildings that yielded at least one point to any
 * spirit — useful for the per-page "X buildings contribute" status line.
 */
public record SpiritTotals(Map<String, Integer> perSpirit, int total, int contributingBuildings) {

    public int pointsFor(String spiritId) {
        Integer v = perSpirit.get(spiritId);
        return v == null ? 0 : v;
    }

    public double shareOf(String spiritId) {
        if (total <= 0) return 0.0;
        return pointsFor(spiritId) / (double) total;
    }

    public static SpiritTotals empty() {
        return new SpiritTotals(Map.of(), 0, 0);
    }
}
