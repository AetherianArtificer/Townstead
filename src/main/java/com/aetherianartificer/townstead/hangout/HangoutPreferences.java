package com.aetherianartificer.townstead.hangout;

import net.minecraft.resources.ResourceLocation;

import java.util.Locale;
import java.util.Map;

/** Pure data-side venue affinity math, kept independent of the MCA runtime boundary. */
final class HangoutPreferences {
    private HangoutPreferences() {}

    static double affinity(HangoutVenue venue, HangoutPolicy policy, String personality) {
        Map<ResourceLocation, Double> profile = profile(
                policy.personalityTagWeights(), personality);
        if (profile.isEmpty() || venue.tags().isEmpty()) return 1D;
        double sum = 0D;
        int matches = 0;
        for (ResourceLocation tag : venue.tags()) {
            Double weight = profile.get(tag);
            if (weight == null) continue;
            sum += weight;
            matches++;
        }
        return matches == 0 ? 1D : sum / matches;
    }

    private static Map<ResourceLocation, Double> profile(
            Map<String, Map<ResourceLocation, Double>> profiles, String personality) {
        if (personality != null) {
            String key = personality.trim().toLowerCase(Locale.ROOT);
            Map<ResourceLocation, Double> exact = profiles.get(key);
            if (exact != null) return exact;
            String alias = key.startsWith("mca:") ? key.substring("mca:".length()) : "mca:" + key;
            exact = profiles.get(alias);
            if (exact != null) return exact;
        }
        return profiles.getOrDefault("default", Map.of());
    }
}
