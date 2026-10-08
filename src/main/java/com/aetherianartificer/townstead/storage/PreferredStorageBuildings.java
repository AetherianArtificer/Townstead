package com.aetherianartificer.townstead.storage;

import com.aetherianartificer.townstead.compat.mca.McaBuildings;
import com.aetherianartificer.townstead.hunger.NearbyItemSources;
import com.aetherianartificer.townstead.profession.ProfessionCapacity;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Building;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Resolves the ordered village buildings a profession names as its preferred stores. */
public final class PreferredStorageBuildings {
    private PreferredStorageBuildings() {}

    public static List<Building> resolve(ServerLevel level, VillagerEntityMCA villager) {
        StoragePreference preference = StoragePreference.forVillager(villager);
        var village = ProfessionCapacity.resolveVillage(villager);
        if (village.isEmpty()) return List.of();

        List<Building> buildings = new ArrayList<>();
        for (Building building : McaBuildings.all(village.get())) {
            if (!building.isComplete()
                    || preference.buildingRank(building.getType())
                    == StoragePreference.FALLBACK_RANK) continue;
            buildings.add(building);
        }
        buildings.sort(Comparator
                .comparingInt((Building building) ->
                        preference.buildingRank(building.getType()))
                .thenComparingDouble(building -> distanceTo(villager, building))
                .thenComparingInt(Building::getId));
        return List.copyOf(buildings);
    }

    /** Deposits in named storage buildings first. The caller owns all later fallbacks. */
    public static int insert(ServerLevel level, VillagerEntityMCA villager, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        int inserted = 0;
        for (Building building : resolve(level, villager)) {
            int before = stack.getCount();
            NearbyItemSources.insertIntoBuildingStorage(level, villager, stack, building);
            inserted += before - stack.getCount();
            if (stack.isEmpty()) break;
        }
        if (inserted > 0) WorksiteStorageIndex.invalidate(level);
        return inserted;
    }

    private static double distanceTo(VillagerEntityMCA villager, Building building) {
        BlockPos center = building.getCenter();
        if (center == null) return Double.MAX_VALUE;
        return villager.distanceToSqr(center.getX() + 0.5, center.getY() + 0.5,
                center.getZ() + 0.5);
    }
}
