package com.aetherianartificer.townstead.hunger;

import com.aetherianartificer.townstead.compat.mca.McaPersonalityCompat;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.relationship.Personality;

import java.util.HashMap;
import java.util.Map;

public record FarmerPersonalityProfile(
        double idleBackoffScale,
        double requestIntervalScale
) {
    private static final FarmerPersonalityProfile DEFAULT = new FarmerPersonalityProfile(
            1.0, 1.0
    );

    // Personality stopped being a Java enum in newer MCA, so EnumMap would fail during class init.
    private static final Map<Personality, FarmerPersonalityProfile> PROFILES = new HashMap<>();

    static {
        // Ids differ across MCA lines; whichever this build lacks is skipped.
        put("unassigned", 1.00, 1.00);
        put("friendly", 0.95, 0.90);
        put("flirty", 1.05, 0.85);
        put("playful", 0.95, 0.80);
        put("witty", 0.95, 0.80);
        put("gloomy", 1.15, 1.25);
        put("sensitive", 1.05, 1.10);
        put("greedy", 0.85, 0.95);
        put("odd", 1.00, 1.00);
        put("crabby", 1.20, 1.40);
        put("grumpy", 1.20, 1.40);
        put("extroverted", 0.90, 0.75);
        put("confident", 0.90, 0.75);
        put("introverted", 1.05, 1.30);
        put("shy", 1.05, 1.30);
        put("relaxed", 1.10, 1.20);
        put("lazy", 1.10, 1.20);
        put("anxious", 1.00, 0.80);
        put("athletic", 1.00, 0.80);
        put("peaceful", 1.00, 1.15);
        put("upbeat", 0.85, 0.85);
        put("peppy", 0.85, 0.85);
    }

    private static void put(String id, double idleBackoffScale, double requestIntervalScale) {
        McaPersonalityCompat.resolve(id).ifPresent(personality ->
                PROFILES.put(personality, new FarmerPersonalityProfile(idleBackoffScale, requestIntervalScale)));
    }

    public static FarmerPersonalityProfile forVillager(VillagerEntityMCA villager) {
        if (villager == null) return DEFAULT;
        Personality personality = villager.getVillagerBrain().getPersonality();
        if (personality == null) return DEFAULT;
        return PROFILES.getOrDefault(personality, DEFAULT);
    }
}
