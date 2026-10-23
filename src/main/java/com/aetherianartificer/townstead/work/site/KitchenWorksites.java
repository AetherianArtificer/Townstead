package com.aetherianartificer.townstead.work.site;

import com.aetherianartificer.townstead.profession.ProfessionCapacity;
import com.aetherianartificer.townstead.profession.ProfessionSites;
import com.aetherianartificer.townstead.profession.def.WorkTaskTypes;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Resolves the buildings and bounds in which cooking stations may be used. */
public final class KitchenWorksites {
    private KitchenWorksites() {}

    public static List<Building> buildings(VillagerEntityMCA villager) {
        if (!(villager.level() instanceof ServerLevel level)) return List.of();
        Optional<Building> assigned = ProfessionSites.assignedSite(
                        level, villager, ProfessionSites.defForTask(WorkTaskTypes.COOK))
                .map(ProfessionSites.Site::building)
                .filter(Objects::nonNull);
        if (assigned.isPresent()) return List.of(assigned.get());
        Optional<Village> village = ProfessionCapacity.resolveVillage(villager);
        if (village.isEmpty()) return List.of();
        return ProfessionCapacity.countedBuildings(
                village.get(), ProfessionSites.defForTask(WorkTaskTypes.COOK));
    }

    public static List<BlockPos> anchors(VillagerEntityMCA villager) {
        return buildings(villager).stream()
                .map(Building::getCenter)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    public static boolean contains(Set<Long> kitchenBounds, BlockPos pos) {
        if (pos == null || kitchenBounds == null || kitchenBounds.isEmpty()) return true;
        if (kitchenBounds.contains(pos.asLong())) return true;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    if (kitchenBounds.contains(pos.offset(dx, dy, dz).asLong())) return true;
                }
            }
        }
        return false;
    }
}
