package com.aetherianartificer.townstead.compat.farmersdelight;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class FarmersDelightCookAssignment {
    private static final String KITCHEN_TYPE_PREFIX = "compat/farmersdelight/kitchen_l";
    private static final String[] COOK_PROFESSION_IDS = new String[] {
            "townstead:cook",
            "chefsdelight:cook",
            "vca:cook",
            "villagerclothingaddition:cook"
    };

    private FarmersDelightCookAssignment() {}

    public static boolean isExternalCookProfession(VillagerProfession profession) {
        if (profession == null) return false;
        for (String id : COOK_PROFESSION_IDS) {
            ResourceLocation key = ResourceLocation.parse(id);
            if (!BuiltInRegistries.VILLAGER_PROFESSION.containsKey(key)) continue;
            if (BuiltInRegistries.VILLAGER_PROFESSION.get(key) == profession) return true;
        }
        return false;
    }

    public static VillagerProfession resolveAssignableCookProfession() {
        for (String id : COOK_PROFESSION_IDS) {
            ResourceLocation key = ResourceLocation.parse(id);
            if (!BuiltInRegistries.VILLAGER_PROFESSION.containsKey(key)) continue;
            VillagerProfession profession = BuiltInRegistries.VILLAGER_PROFESSION.get(key);
            if (profession != null && profession != VillagerProfession.NONE) return profession;
        }
        return null;
    }

    public static boolean canVillagerWorkAsCook(ServerLevel level, VillagerEntityMCA villager) {
        Optional<Village> villageOpt = resolveVillage(villager);
        if (villageOpt.isEmpty()) return false;
        Village village = villageOpt.get();
        if (!isEligibleVillageMember(village, villager)) return false;

        int cookSlots = totalCookSlots(village);
        if (cookSlots <= 0) return false;

        List<VillagerEntityMCA> cooks = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (VillagerEntityMCA resident : village.getResidents(level)) {
            if (!isExternalCookProfession(resident.getVillagerData().getProfession())) continue;
            if (!seen.add(resident.getUUID().toString())) continue;
            cooks.add(resident);
        }
        if (isExternalCookProfession(villager.getVillagerData().getProfession())
                && seen.add(villager.getUUID().toString())) {
            cooks.add(villager);
        }
        cooks.sort(Comparator.comparing(v -> v.getUUID().toString()));

        for (int i = 0; i < cooks.size() && i < cookSlots; i++) {
            if (cooks.get(i).getUUID().equals(villager.getUUID())) return true;
        }
        return false;
    }

    public static boolean hasAvailableCookSlot(ServerLevel level, VillagerEntityMCA villager) {
        Optional<Village> villageOpt = resolveVillage(villager);
        if (villageOpt.isEmpty()) return false;
        Village village = villageOpt.get();
        if (!isEligibleVillageMember(village, villager)) return false;

        int cookSlots = totalCookSlots(village);
        if (cookSlots <= 0) return false;

        int activeCooks = 0;
        for (VillagerEntityMCA resident : village.getResidents(level)) {
            if (isExternalCookProfession(resident.getVillagerData().getProfession())) {
                activeCooks++;
            }
        }
        return activeCooks < cookSlots;
    }

    public static boolean shouldLoseCookProfession(ServerLevel level, VillagerEntityMCA villager) {
        Optional<Village> home = villager.getResidency().getHomeVillage();
        if (home.isEmpty()) return true;
        Village village = home.get();
        return totalCookSlots(village) <= 0;
    }

    private static Optional<Village> resolveVillage(VillagerEntityMCA villager) {
        Optional<Village> home = villager.getResidency().getHomeVillage();
        if (home.isPresent() && home.get().isWithinBorder(villager)) return home;
        Optional<Village> nearest = Village.findNearest(villager);
        if (nearest.isPresent() && nearest.get().isWithinBorder(villager)) return nearest;
        return Optional.empty();
    }

    private static boolean isEligibleVillageMember(Village village, VillagerEntityMCA villager) {
        if (!village.isWithinBorder(villager)) return false;

        Optional<Village> home = villager.getResidency().getHomeVillage();
        if (home.isPresent() && home.get().getId() == village.getId()) return true;

        UUID id = villager.getUUID();
        return village.getResidentsUUIDs().anyMatch(id::equals);
    }

    public static int totalCookSlots(Village village) {
        int total = 0;
        for (Building building : village.getBuildings().values()) {
            String type = building.getType();
            if (!isKitchenType(type)) continue;
            total += slotsForKitchenTier(tierFromKitchenType(type));
        }
        return total;
    }

    public static boolean isKitchenType(String buildingTypeId) {
        return buildingTypeId != null && buildingTypeId.startsWith(KITCHEN_TYPE_PREFIX);
    }

    private static int tierFromKitchenType(String buildingTypeId) {
        if (buildingTypeId == null || !buildingTypeId.startsWith(KITCHEN_TYPE_PREFIX)) return 0;
        try {
            return Integer.parseInt(buildingTypeId.substring(KITCHEN_TYPE_PREFIX.length()));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static int slotsForKitchenTier(int tier) {
        return switch (tier) {
            case 1, 2 -> 1;
            case 3, 4 -> 2;
            case 5 -> 3;
            default -> 0;
        };
    }
}
