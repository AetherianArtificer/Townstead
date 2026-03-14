package com.aetherianartificer.townstead.compat.farmersdelight;

import com.aetherianartificer.townstead.Townstead;
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

public final class FarmersDelightBaristaAssignment {
    private static final String[] BARISTA_PROFESSION_IDS = new String[] {
            "townstead:barista"
    };

    private FarmersDelightBaristaAssignment() {}

    public static boolean isBaristaProfession(VillagerProfession profession) {
        if (profession == null) return false;
        for (String id : BARISTA_PROFESSION_IDS) {
            //? if >=1.21 {
            ResourceLocation key = ResourceLocation.parse(id);
            //?} else {
            /*ResourceLocation key = new ResourceLocation(id);
            *///?}
            if (!BuiltInRegistries.VILLAGER_PROFESSION.containsKey(key)) continue;
            if (BuiltInRegistries.VILLAGER_PROFESSION.get(key) == profession) return true;
        }
        return false;
    }

    public static boolean canVillagerWorkAsBarista(ServerLevel level, VillagerEntityMCA villager) {
        return assignedCafe(level, villager).isPresent();
    }

    public static boolean hasAvailableBaristaSlot(ServerLevel level, VillagerEntityMCA villager) {
        Optional<Village> villageOpt = FarmersDelightCookAssignment.resolveVillage(villager);
        if (villageOpt.isEmpty()) return false;
        Village village = villageOpt.get();
        if (!village.isWithinBorder(villager)) return false;

        List<CafeSlot> slots = buildCafeSlots(village);
        if (slots.isEmpty()) return false;

        int activeBaristas = 0;
        for (VillagerEntityMCA resident : village.getResidents(level)) {
            if (isBaristaProfession(resident.getVillagerData().getProfession())) {
                activeBaristas++;
            }
        }
        return activeBaristas < slots.size();
    }

    public static int effectiveCafeTier(ServerLevel level, VillagerEntityMCA villager) {
        Optional<Building> cafe = assignedCafe(level, villager);
        if (cafe.isPresent()) {
            return Math.max(0, BaristaTierRules.cafeTierFromType(cafe.get().getType()));
        }
        Optional<Village> village = FarmersDelightCookAssignment.resolveVillage(villager);
        return village.map(FarmersDelightBaristaAssignment::highestCafeTier).orElse(0);
    }

    public static int effectiveRecipeTier(ServerLevel level, VillagerEntityMCA villager) {
        return effectiveCafeTier(level, villager);
    }

    public static int highestCafeTier(Village village) {
        int best = 0;
        for (Building building : village.getBuildings().values()) {
            String type = building.getType();
            if (!BaristaTierRules.isCafeType(type)) continue;
            best = Math.max(best, BaristaTierRules.cafeTierFromType(type));
        }
        return best;
    }

    public static Optional<Building> assignedCafe(ServerLevel level, VillagerEntityMCA villager) {
        Optional<Village> villageOpt = FarmersDelightCookAssignment.resolveVillage(villager);
        if (villageOpt.isEmpty()) return Optional.empty();
        Village village = villageOpt.get();
        if (!village.isWithinBorder(villager)) return Optional.empty();

        List<CafeSlot> slots = buildCafeSlots(village);
        if (slots.isEmpty()) return Optional.empty();

        List<VillagerEntityMCA> baristas = sortedBaristaResidents(level, village);
        if (isBaristaProfession(villager.getVillagerData().getProfession())) {
            boolean present = baristas.stream().anyMatch(v -> v.getUUID().equals(villager.getUUID()));
            if (!present) {
                baristas.add(villager);
                baristas.sort(Comparator.comparing(v -> v.getUUID().toString()));
            }
        }
        int idx = -1;
        for (int i = 0; i < baristas.size(); i++) {
            if (baristas.get(i).getUUID().equals(villager.getUUID())) {
                idx = i;
                break;
            }
        }
        if (idx < 0 || idx >= slots.size()) return Optional.empty();
        return Optional.of(slots.get(idx).building());
    }

    public static Set<Long> assignedCafeBounds(ServerLevel level, VillagerEntityMCA villager) {
        Optional<Building> cafe = assignedCafe(level, villager);
        if (cafe.isEmpty()) return Set.of();
        Set<Long> bounds = new HashSet<>();
        for (BlockPos bp : (Iterable<BlockPos>) cafe.get().getBlockPosStream()::iterator) {
            bounds.add(bp.asLong());
        }
        return bounds;
    }

    private static List<Building> sortedCafes(Village village) {
        List<Building> cafes = new ArrayList<>();
        for (Building building : village.getBuildings().values()) {
            if (!BaristaTierRules.isCafeType(building.getType())) continue;
            cafes.add(building);
        }
        cafes.sort((a, b) -> {
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
        return cafes;
    }

    private static List<VillagerEntityMCA> sortedBaristaResidents(ServerLevel level, Village village) {
        List<VillagerEntityMCA> baristas = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (VillagerEntityMCA resident : village.getResidents(level)) {
            if (!isBaristaProfession(resident.getVillagerData().getProfession())) continue;
            if (!seen.add(resident.getUUID())) continue;
            baristas.add(resident);
        }
        baristas.sort(Comparator.comparing(v -> v.getUUID().toString()));
        return baristas;
    }

    private static List<CafeSlot> buildCafeSlots(Village village) {
        List<CafeSlot> slots = new ArrayList<>();
        for (Building cafe : sortedCafes(village)) {
            int tier = BaristaTierRules.cafeTierFromType(cafe.getType());
            int slotCount = BaristaTierRules.slotsForTier(tier);
            for (int i = 0; i < slotCount; i++) {
                slots.add(new CafeSlot(cafe, i));
            }
        }
        return slots;
    }

    private record CafeSlot(Building building, int ordinal) {}
}
