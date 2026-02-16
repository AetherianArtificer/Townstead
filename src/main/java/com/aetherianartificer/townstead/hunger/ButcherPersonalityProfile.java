package com.aetherianartificer.townstead.hunger;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.relationship.Personality;

import java.util.EnumMap;
import java.util.Map;

public record ButcherPersonalityProfile(
        double throughputScale,
        double idleBackoffScale,
        double requestIntervalScale,
        double stockCadenceScale
) {
    private static final ButcherPersonalityProfile DEFAULT = new ButcherPersonalityProfile(1.0, 1.0, 1.0, 1.0);
    private static final Map<Personality, ButcherPersonalityProfile> PROFILES = new EnumMap<>(Personality.class);

    static {
        PROFILES.put(Personality.UNASSIGNED, DEFAULT);
        PROFILES.put(Personality.FRIENDLY, new ButcherPersonalityProfile(1.00, 0.95, 0.90, 0.95));
        PROFILES.put(Personality.FLIRTY, new ButcherPersonalityProfile(0.95, 1.05, 0.85, 1.00));
        PROFILES.put(Personality.PLAYFUL, new ButcherPersonalityProfile(1.10, 0.95, 0.85, 0.90));
        PROFILES.put(Personality.GLOOMY, new ButcherPersonalityProfile(0.90, 1.15, 1.25, 1.10));
        PROFILES.put(Personality.SENSITIVE, new ButcherPersonalityProfile(0.95, 1.10, 1.10, 1.05));
        PROFILES.put(Personality.GREEDY, new ButcherPersonalityProfile(1.20, 0.90, 0.95, 0.80));
        PROFILES.put(Personality.ODD, DEFAULT);
        PROFILES.put(Personality.CRABBY, new ButcherPersonalityProfile(0.85, 1.20, 1.40, 1.15));
        PROFILES.put(Personality.EXTROVERTED, new ButcherPersonalityProfile(1.05, 0.90, 0.75, 0.90));
        PROFILES.put(Personality.INTROVERTED, new ButcherPersonalityProfile(0.95, 1.05, 1.30, 1.05));
        PROFILES.put(Personality.RELAXED, new ButcherPersonalityProfile(0.90, 1.10, 1.20, 1.10));
        PROFILES.put(Personality.ANXIOUS, new ButcherPersonalityProfile(0.95, 1.00, 0.80, 1.00));
        PROFILES.put(Personality.PEACEFUL, new ButcherPersonalityProfile(0.98, 1.00, 1.15, 1.00));
        PROFILES.put(Personality.UPBEAT, new ButcherPersonalityProfile(1.15, 0.85, 0.85, 0.85));
    }

    public static ButcherPersonalityProfile forVillager(VillagerEntityMCA villager) {
        if (villager == null) return DEFAULT;
        Personality personality = villager.getVillagerBrain().getPersonality();
        if (personality == null) return DEFAULT;
        return PROFILES.getOrDefault(personality, DEFAULT);
    }
}
