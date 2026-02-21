package com.aetherianartificer.townstead.compat.farmersdelight;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.hunger.CookProgressData;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.core.BlockPos;
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
        return assignedKitchen(level, villager).isPresent();
    }

    public static boolean hasAvailableCookSlot(ServerLevel level, VillagerEntityMCA villager) {
        Optional<Village> villageOpt = resolveVillage(villager);
        if (villageOpt.isEmpty()) return false;
        Village village = villageOpt.get();
        if (!isEligibleVillageMember(village, villager)) return false;

        List<KitchenSlot> slots = buildKitchenSlots(village);
        if (slots.isEmpty()) return false;

        int activeCooks = 0;
        for (VillagerEntityMCA resident : village.getResidents(level)) {
            if (isExternalCookProfession(resident.getVillagerData().getProfession())) {
                activeCooks++;
            }
        }
        return activeCooks < slots.size();
    }

    public static boolean shouldLoseCookProfession(ServerLevel level, VillagerEntityMCA villager) {
        return assignedKitchen(level, villager).isEmpty();
    }

    public static Optional<Village> resolveVillage(VillagerEntityMCA villager) {
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
        return buildKitchenSlots(village).size();
    }

    public static int highestKitchenTier(Village village) {
        int best = 0;
        for (Building building : village.getBuildings().values()) {
            String type = building.getType();
            if (!isKitchenType(type)) continue;
            best = Math.max(best, kitchenTierFromType(type));
        }
        return best;
    }

    public static int effectiveKitchenTier(ServerLevel level, VillagerEntityMCA villager) {
        Optional<Building> kitchen = assignedKitchen(level, villager);
        if (kitchen.isPresent()) {
            return Math.max(0, kitchenTierFromType(kitchen.get().getType()));
        }
        Optional<Village> village = resolveVillage(villager);
        return village.map(FarmersDelightCookAssignment::highestKitchenTier).orElse(0);
    }

    /**
     * Returns the effective recipe tier for this cook, considering both the
     * kitchen building tier and the cook's personal progression tier.
     */
    public static int effectiveRecipeTier(ServerLevel level, VillagerEntityMCA villager) {
        int kitchenTier = effectiveKitchenTier(level, villager);
        int cookTier = CookProgressData.getTier(villager.getData(Townstead.HUNGER_DATA));
        return Math.min(kitchenTier, cookTier);
    }

    public static Optional<Building> assignedKitchen(ServerLevel level, VillagerEntityMCA villager) {
        Optional<Village> villageOpt = resolveVillage(villager);
        if (villageOpt.isEmpty()) return Optional.empty();
        Village village = villageOpt.get();
        if (!isEligibleVillageMember(village, villager)) return Optional.empty();

        List<KitchenSlot> slots = buildKitchenSlots(village);
        if (slots.isEmpty()) return Optional.empty();

        List<VillagerEntityMCA> cooks = sortedCookResidents(level, village);
        if (isExternalCookProfession(villager.getVillagerData().getProfession())) {
            boolean present = cooks.stream().anyMatch(v -> v.getUUID().equals(villager.getUUID()));
            if (!present) {
                cooks.add(villager);
                cooks.sort(Comparator.comparing(v -> v.getUUID().toString()));
            }
        }
        int idx = -1;
        for (int i = 0; i < cooks.size(); i++) {
            if (cooks.get(i).getUUID().equals(villager.getUUID())) {
                idx = i;
                break;
            }
        }
        if (idx < 0 || idx >= slots.size()) return Optional.empty();
        return Optional.of(slots.get(idx).building());
    }

    public static Set<Long> assignedKitchenBounds(ServerLevel level, VillagerEntityMCA villager) {
        Optional<Building> kitchen = assignedKitchen(level, villager);
        if (kitchen.isEmpty()) return Set.of();
        Set<Long> bounds = new HashSet<>();
        for (BlockPos bp : (Iterable<BlockPos>) kitchen.get().getBlockPosStream()::iterator) {
            bounds.add(bp.asLong());
        }
        return bounds;
    }

    public static boolean isKitchenType(String buildingTypeId) {
        return CookTierRules.isKitchenType(buildingTypeId);
    }

    static int kitchenTierFromType(String buildingTypeId) {
        return CookTierRules.kitchenTierFromType(buildingTypeId);
    }

    static int slotsForKitchenType(String buildingTypeId) {
        return CookTierRules.slotsForKitchenType(buildingTypeId);
    }

    private static List<Building> sortedKitchens(Village village) {
        List<Building> kitchens = new ArrayList<>();
        for (Building building : village.getBuildings().values()) {
            if (!isKitchenType(building.getType())) continue;
            kitchens.add(building);
        }
        kitchens.sort((a, b) -> {
            BlockPos ac = a.getCenter();
            BlockPos bc = b.getCenter();
            if (ac != null && bc != null) {
                if (ac.getY() != bc.getY()) return Integer.compare(ac.getY(), bc.getY());
                if (ac.getZ() != bc.getZ()) return Integer.compare(ac.getZ(), bc.getZ());
                if (ac.getX() != bc.getX()) return Integer.compare(ac.getX(), bc.getX());
            } else if (ac != null) {
                return -1;
            } else if (bc != null) {
                return 1;
            }
            return a.getType().compareTo(b.getType());
        });
        return kitchens;
    }

    private static List<VillagerEntityMCA> sortedCookResidents(ServerLevel level, Village village) {
        List<VillagerEntityMCA> cooks = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (VillagerEntityMCA resident : village.getResidents(level)) {
            if (!isExternalCookProfession(resident.getVillagerData().getProfession())) continue;
            if (!seen.add(resident.getUUID())) continue;
            cooks.add(resident);
        }
        cooks.sort(Comparator.comparing(v -> v.getUUID().toString()));
        return cooks;
    }

    private static List<KitchenSlot> buildKitchenSlots(Village village) {
        List<KitchenSlot> slots = new ArrayList<>();
        for (Building kitchen : sortedKitchens(village)) {
            int tier = kitchenTierFromType(kitchen.getType());
            int slotCount = slotsForTier(tier);
            for (int i = 0; i < slotCount; i++) {
                slots.add(new KitchenSlot(kitchen, i));
            }
        }
        return slots;
    }

    private record KitchenSlot(Building building, int ordinal) {}

    static int slotsForTier(int tier) {
        return CookTierRules.slotsForTier(tier);
    }
}
