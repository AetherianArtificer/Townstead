package com.aetherianartificer.townstead.upgrade;

import net.conczin.mca.resources.BuildingTypes;
import net.conczin.mca.resources.data.BuildingType;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

public final class BuildingTierReconciler {
    private BuildingTierReconciler() {}

    public static void reconcileVillage(Village village) {
        if (village == null) return;
        for (Building building : village.getBuildings().values()) {
            reconcileBuilding(building);
        }
    }

    private static void reconcileBuilding(Building building) {
        if (building == null || !building.isTypeForced()) return;
        String currentType = building.getType();
        TierRef ref = parseTierRef(currentType);
        if (ref == null) return;

        String best = highestSatisfiableTierType(ref.prefix(), building);
        if (best == null) {
            // No longer satisfies any tier in this chain: drop force and let MCA classify normally.
            building.setTypeForced(false);
            building.determineType();
            return;
        }
        if (!best.equals(currentType)) {
            building.setType(best);
        }
    }

    private static String highestSatisfiableTierType(String prefix, Building building) {
        String best = null;
        for (int tier = 1; tier <= 32; tier++) {
            String candidateId = prefix + "_l" + tier;
            if (!BuildingTypes.getInstance().getBuildingTypes().containsKey(candidateId)) break;
            BuildingType bt = BuildingTypes.getInstance().getBuildingType(candidateId);
            if (bt == null) break;
            if (!meetsRequirements(bt, building)) continue;
            best = candidateId;
        }
        return best;
    }

    private static boolean meetsRequirements(BuildingType type, Building building) {
        Map<ResourceLocation, List<net.minecraft.core.BlockPos>> available = type.getGroups(building.getBlocks());
        return type.getGroups().entrySet().stream()
                .noneMatch(e -> !available.containsKey(e.getKey()) || available.get(e.getKey()).size() < e.getValue());
    }

    private static TierRef parseTierRef(String typeId) {
        if (typeId == null) return null;
        int idx = typeId.lastIndexOf("_l");
        if (idx <= 0 || idx >= typeId.length() - 2) return null;
        String suffix = typeId.substring(idx + 2);
        if (!suffix.chars().allMatch(Character::isDigit)) return null;
        return new TierRef(typeId.substring(0, idx), Integer.parseInt(suffix));
    }

    private record TierRef(String prefix, int tier) {}
}

