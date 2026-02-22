package com.aetherianartificer.townstead.compat.farmersdelight;

final class CookTierRules {
    private static final String KITCHEN_TYPE_PREFIX = "compat/farmersdelight/kitchen_l";

    private CookTierRules() {}

    static boolean isKitchenType(String buildingTypeId) {
        return buildingTypeId != null && buildingTypeId.startsWith(KITCHEN_TYPE_PREFIX);
    }

    static int kitchenTierFromType(String buildingTypeId) {
        if (buildingTypeId == null || !buildingTypeId.startsWith(KITCHEN_TYPE_PREFIX)) return 0;
        try {
            return Integer.parseInt(buildingTypeId.substring(KITCHEN_TYPE_PREFIX.length()));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    static int slotsForKitchenType(String buildingTypeId) {
        return slotsForTier(kitchenTierFromType(buildingTypeId));
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
