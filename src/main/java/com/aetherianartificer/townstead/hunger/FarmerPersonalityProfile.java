package com.aetherianartificer.townstead.hunger;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.relationship.Personality;

import java.util.EnumMap;
import java.util.Map;

public record FarmerPersonalityProfile(
        double expansionScale,
        double hydrationScale,
        double idleBackoffScale,
        double requestIntervalScale,
        int seedReserveBonus,
        boolean prioritizeIrrigation
) {
    private static final FarmerPersonalityProfile DEFAULT = new FarmerPersonalityProfile(
            1.0, 1.0, 1.0, 1.0, 0, false
    );

    private static final Map<Personality, FarmerPersonalityProfile> PROFILES = new EnumMap<>(Personality.class);

    static {
        PROFILES.put(Personality.UNASSIGNED, DEFAULT);
        PROFILES.put(Personality.FRIENDLY, new FarmerPersonalityProfile(1.00, 1.00, 0.95, 0.90, 0, false));
        PROFILES.put(Personality.FLIRTY, new FarmerPersonalityProfile(0.90, 0.90, 1.05, 0.85, 0, false));
        PROFILES.put(Personality.PLAYFUL, new FarmerPersonalityProfile(1.10, 0.90, 0.95, 0.80, 0, false));
        PROFILES.put(Personality.GLOOMY, new FarmerPersonalityProfile(0.85, 1.05, 1.15, 1.25, 1, true));
        PROFILES.put(Personality.SENSITIVE, new FarmerPersonalityProfile(0.90, 1.20, 1.05, 1.10, 1, true));
        PROFILES.put(Personality.GREEDY, new FarmerPersonalityProfile(1.25, 0.90, 0.85, 0.95, -1, false));
        PROFILES.put(Personality.ODD, new FarmerPersonalityProfile(1.00, 1.00, 1.00, 1.00, 0, false));
        PROFILES.put(Personality.CRABBY, new FarmerPersonalityProfile(0.80, 1.10, 1.20, 1.40, 1, true));
        PROFILES.put(Personality.EXTROVERTED, new FarmerPersonalityProfile(1.05, 0.95, 0.90, 0.75, 0, false));
        PROFILES.put(Personality.INTROVERTED, new FarmerPersonalityProfile(1.00, 1.10, 1.05, 1.30, 1, true));
        PROFILES.put(Personality.RELAXED, new FarmerPersonalityProfile(0.90, 0.90, 1.10, 1.20, 0, false));
        PROFILES.put(Personality.ANXIOUS, new FarmerPersonalityProfile(0.85, 1.25, 1.00, 0.80, 2, true));
        PROFILES.put(Personality.PEACEFUL, new FarmerPersonalityProfile(1.00, 1.10, 1.00, 1.15, 1, true));
        PROFILES.put(Personality.UPBEAT, new FarmerPersonalityProfile(1.15, 0.95, 0.85, 0.85, 0, false));
    }

    public static FarmerPersonalityProfile forVillager(VillagerEntityMCA villager) {
        if (villager == null) return DEFAULT;
        Personality personality = villager.getVillagerBrain().getPersonality();
        if (personality == null) return DEFAULT;
        return PROFILES.getOrDefault(personality, DEFAULT);
    }
}
