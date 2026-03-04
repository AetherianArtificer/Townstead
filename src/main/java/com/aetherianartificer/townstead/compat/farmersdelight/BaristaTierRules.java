package com.aetherianartificer.townstead.compat.farmersdelight;

final class BaristaTierRules {
    private static final String CAFE_TYPE_PREFIX = "compat/rusticdelight/cafe_l";

    private BaristaTierRules() {}

    static boolean isCafeType(String buildingTypeId) {
        return buildingTypeId != null && buildingTypeId.startsWith(CAFE_TYPE_PREFIX);
    }

    static int cafeTierFromType(String buildingTypeId) {
        if (buildingTypeId == null || !buildingTypeId.startsWith(CAFE_TYPE_PREFIX)) return 0;
        try {
            return Integer.parseInt(buildingTypeId.substring(CAFE_TYPE_PREFIX.length()));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    static int slotsForCafeType(String buildingTypeId) {
        return slotsForTier(cafeTierFromType(buildingTypeId));
    }

    static int slotsForTier(int tier) {
        return switch (tier) {
            case 1, 2 -> 1;
            case 3, 4 -> 2;
            case 5 -> 3;
            default -> 0;
        };
    }
}
