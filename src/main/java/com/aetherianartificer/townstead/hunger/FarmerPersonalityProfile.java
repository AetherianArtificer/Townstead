package com.aetherianartificer.townstead.hunger;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.relationship.Personality;

import java.util.EnumMap;
import java.util.Map;

public record FarmerPersonalityProfile(
        double idleBackoffScale,
        double requestIntervalScale
) {
    private static final FarmerPersonalityProfile DEFAULT = new FarmerPersonalityProfile(
            1.0, 1.0
    );

    private static final Map<Personality, FarmerPersonalityProfile> PROFILES = new EnumMap<>(Personality.class);

    static {
        PROFILES.put(Personality.UNASSIGNED, DEFAULT);
        PROFILES.put(Personality.FRIENDLY, new FarmerPersonalityProfile(0.95, 0.90));
        PROFILES.put(Personality.FLIRTY, new FarmerPersonalityProfile(1.05, 0.85));
        //? if neoforge {
        PROFILES.put(Personality.PLAYFUL, new FarmerPersonalityProfile(0.95, 0.80));
        //?} else {
        /*PROFILES.put(Personality.WITTY, new FarmerPersonalityProfile(0.95, 0.80));
        *///?}
        PROFILES.put(Personality.GLOOMY, new FarmerPersonalityProfile(1.15, 1.25));
        PROFILES.put(Personality.SENSITIVE, new FarmerPersonalityProfile(1.05, 1.10));
        PROFILES.put(Personality.GREEDY, new FarmerPersonalityProfile(0.85, 0.95));
        PROFILES.put(Personality.ODD, new FarmerPersonalityProfile(1.00, 1.00));
        //? if neoforge {
        PROFILES.put(Personality.CRABBY, new FarmerPersonalityProfile(1.20, 1.40));
        PROFILES.put(Personality.EXTROVERTED, new FarmerPersonalityProfile(0.90, 0.75));
        PROFILES.put(Personality.INTROVERTED, new FarmerPersonalityProfile(1.05, 1.30));
        PROFILES.put(Personality.RELAXED, new FarmerPersonalityProfile(1.10, 1.20));
        PROFILES.put(Personality.ANXIOUS, new FarmerPersonalityProfile(1.00, 0.80));
        PROFILES.put(Personality.PEACEFUL, new FarmerPersonalityProfile(1.00, 1.15));
        PROFILES.put(Personality.UPBEAT, new FarmerPersonalityProfile(0.85, 0.85));
        //?} else {
        /*PROFILES.put(Personality.GRUMPY, new FarmerPersonalityProfile(1.20, 1.40));
        PROFILES.put(Personality.CONFIDENT, new FarmerPersonalityProfile(0.90, 0.75));
        PROFILES.put(Personality.SHY, new FarmerPersonalityProfile(1.05, 1.30));
        PROFILES.put(Personality.LAZY, new FarmerPersonalityProfile(1.10, 1.20));
        PROFILES.put(Personality.ATHLETIC, new FarmerPersonalityProfile(1.00, 0.80));
        PROFILES.put(Personality.PEPPY, new FarmerPersonalityProfile(0.85, 0.85));
        *///?}
    }

    public static FarmerPersonalityProfile forVillager(VillagerEntityMCA villager) {
        if (villager == null) return DEFAULT;
        Personality personality = villager.getVillagerBrain().getPersonality();
        if (personality == null) return DEFAULT;
        return PROFILES.getOrDefault(personality, DEFAULT);
    }
}
