package com.aetherianartificer.townstead.villager;

/**
 * Per-profession XP tuning. Replaces the four near-identical {@code *ProgressData}
 * classes (Farmer/Butcher/Cook/Shepherd) with one shared engine
 * ({@link ProfessionProgress}) parameterised by this enum.
 *
 * <p>Thresholds, daily cap, and max XP are carried over verbatim from those
 * classes so progression is unchanged. See {@code project_xp_standardization.md}.
 */
public enum ProfessionXpType {
    FARMER("farmer", new int[]{0, 120, 320, 700, 1300}, 240, 200000),
    BUTCHER("butcher", new int[]{0, 20, 60, 120, 200}, 60, 1000),
    COOK("cook", new int[]{0, 110, 300, 660, 1250}, 230, 200000),
    SHEPHERD("shepherd", new int[]{0, 20, 60, 120, 200}, 60, 1000);

    private final String id;
    private final int[] tierThresholds;
    private final int dailyXpCap;
    private final int maxXp;

    ProfessionXpType(String id, int[] tierThresholds, int dailyXpCap, int maxXp) {
        this.id = id;
        this.tierThresholds = tierThresholds;
        this.dailyXpCap = dailyXpCap;
        this.maxXp = maxXp;
    }

    public String id() {
        return id;
    }

    /** This built-in expressed as the unified runtime spec. */
    public ProgressionSpec spec() {
        return new ProgressionSpec(tierThresholds, dailyXpCap, maxXp);
    }

    public int dailyXpCap() {
        return dailyXpCap;
    }

    public int maxXp() {
        return maxXp;
    }

    /** XP required to be standing in {@code tier} (index 0..4). */
    public int thresholdForTier(int tier) {
        return tierThresholds[tier];
    }

    public int tierForXp(int xp) {
        if (xp >= tierThresholds[4]) return 5;
        if (xp >= tierThresholds[3]) return 4;
        if (xp >= tierThresholds[2]) return 3;
        if (xp >= tierThresholds[1]) return 2;
        return 1;
    }
}
