package com.aetherianartificer.townstead.hunger;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.relationship.Personality;

import java.util.EnumMap;
import java.util.Map;

public record FarmerPersonalityProfile(
        double expansionScale,
        double idleBackoffScale,
        double requestIntervalScale,
        int seedReserveBonus
) {
    private static final FarmerPersonalityProfile DEFAULT = new FarmerPersonalityProfile(
            1.0, 1.0, 1.0, 0
    );

    private static final Map<Personality, FarmerPersonalityProfile> PROFILES = new EnumMap<>(Personality.class);

    static {
        PROFILES.put(Personality.UNASSIGNED, DEFAULT);
        PROFILES.put(Personality.FRIENDLY, new FarmerPersonalityProfile(1.00, 0.95, 0.90, 0));
        PROFILES.put(Personality.FLIRTY, new FarmerPersonalityProfile(0.90, 1.05, 0.85, 0));
        //? if neoforge {
        PROFILES.put(Personality.PLAYFUL, new FarmerPersonalityProfile(1.10, 0.95, 0.80, 0));
        //?} else {
        /*PROFILES.put(Personality.WITTY, new FarmerPersonalityProfile(1.10, 0.95, 0.80, 0));
        *///?}
        PROFILES.put(Personality.GLOOMY, new FarmerPersonalityProfile(0.85, 1.15, 1.25, 1));
        PROFILES.put(Personality.SENSITIVE, new FarmerPersonalityProfile(0.90, 1.05, 1.10, 1));
        PROFILES.put(Personality.GREEDY, new FarmerPersonalityProfile(1.25, 0.85, 0.95, -1));
        PROFILES.put(Personality.ODD, new FarmerPersonalityProfile(1.00, 1.00, 1.00, 0));
        //? if neoforge {
        PROFILES.put(Personality.CRABBY, new FarmerPersonalityProfile(0.80, 1.20, 1.40, 1));
        PROFILES.put(Personality.EXTROVERTED, new FarmerPersonalityProfile(1.05, 0.90, 0.75, 0));
        PROFILES.put(Personality.INTROVERTED, new FarmerPersonalityProfile(1.00, 1.05, 1.30, 1));
        PROFILES.put(Personality.RELAXED, new FarmerPersonalityProfile(0.90, 1.10, 1.20, 0));
        PROFILES.put(Personality.ANXIOUS, new FarmerPersonalityProfile(0.85, 1.00, 0.80, 2));
        PROFILES.put(Personality.PEACEFUL, new FarmerPersonalityProfile(1.00, 1.00, 1.15, 1));
        PROFILES.put(Personality.UPBEAT, new FarmerPersonalityProfile(1.15, 0.85, 0.85, 0));
        //?} else {
        /*PROFILES.put(Personality.GRUMPY, new FarmerPersonalityProfile(0.80, 1.20, 1.40, 1));
        PROFILES.put(Personality.CONFIDENT, new FarmerPersonalityProfile(1.05, 0.90, 0.75, 0));
        PROFILES.put(Personality.SHY, new FarmerPersonalityProfile(1.00, 1.05, 1.30, 1));
        PROFILES.put(Personality.LAZY, new FarmerPersonalityProfile(0.90, 1.10, 1.20, 0));
        PROFILES.put(Personality.ATHLETIC, new FarmerPersonalityProfile(0.85, 1.00, 0.80, 2));
        PROFILES.put(Personality.PEPPY, new FarmerPersonalityProfile(1.15, 0.85, 0.85, 0));
        *///?}
    }

    public static FarmerPersonalityProfile forVillager(VillagerEntityMCA villager) {
        if (villager == null) return DEFAULT;
        Personality personality = villager.getVillagerBrain().getPersonality();
        if (personality == null) return DEFAULT;
        return PROFILES.getOrDefault(personality, DEFAULT);
    }
}
