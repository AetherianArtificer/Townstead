package com.aetherianartificer.townstead.hunger;

public final class ButcherPolicyClientStore {
    private static volatile String profileId = "smokehouse_core";
    private static volatile int tier = 5;
    private static volatile int areaCount = 0;

    private ButcherPolicyClientStore() {}

    public static void set(String profileIdValue, int tierValue, int areaCountValue) {
        profileId = normalizeProfileId(profileIdValue);
        tier = normalizeTier(tierValue);
        areaCount = Math.max(0, areaCountValue);
    }

    public static String getProfileId() {
        return profileId;
    }

    public static int getTier() {
        return tier;
    }

    public static int getAreaCount() {
        return areaCount;
    }

    public static void clear() {
        profileId = "smokehouse_core";
        tier = 5;
        areaCount = 0;
    }

    private static String normalizeProfileId(String value) {
        if (value == null || value.isBlank()) return "smokehouse_core";
        return value.trim();
    }

    private static int normalizeTier(int tierValue) {
        return Math.max(1, Math.min(tierValue, 5));
    }
}
