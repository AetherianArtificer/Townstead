package com.aetherianartificer.townstead.farming;

public final class FarmingPolicyClientStore {
    private static volatile String patternId = "starter_rows";
    private static volatile int tier = 5;
    private static volatile int areaCount = 0;

    private FarmingPolicyClientStore() {}

    public static void set(String patternIdValue, int tierValue, int areaCountValue) {
        patternId = (patternIdValue == null || patternIdValue.isBlank()) ? "starter_rows" : patternIdValue;
        tier = Math.max(1, Math.min(5, tierValue));
        areaCount = Math.max(0, areaCountValue);
    }

    public static String getPatternId() {
        return patternId;
    }

    public static int getTier() {
        return tier;
    }

    public static int getAreaCount() {
        return areaCount;
    }

    public static void clear() {
        patternId = "starter_rows";
        tier = 5;
        areaCount = 0;
    }
}
