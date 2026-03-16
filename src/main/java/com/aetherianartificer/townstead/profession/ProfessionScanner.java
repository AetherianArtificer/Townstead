package com.aetherianartificer.townstead.profession;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.compat.ModCompat;
import com.aetherianartificer.townstead.compat.farmersdelight.FarmersDelightBaristaAssignment;
import com.aetherianartificer.townstead.compat.farmersdelight.FarmersDelightCookAssignment;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.VillagerProfession;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Server-side logic to determine which professions are available in a village.
 */
public final class ProfessionScanner {

    private ProfessionScanner() {}

    public record ScanResult(List<String> professionIds, List<Integer> usedSlots, List<Integer> maxSlots) {}

    /**
     * Scans the village the player is in and returns available professions with slot info.
     */
    public static ScanResult scanAvailableProfessions(ServerPlayer player) {
        Set<String> result = new LinkedHashSet<>();
        Map<String, int[]> slotInfo = new HashMap<>(); // profId -> {used, max}, max=-1 for unlimited

        // Always include NONE (strip profession)
        result.add(professionKey(VillagerProfession.NONE));

        // Find the village the player is in
        Optional<Village> villageOpt = findPlayerVillage(player);
        if (villageOpt.isEmpty()) return buildResult(result, slotInfo);
        Village village = villageOpt.get();

        ServerLevel level = player.serverLevel();

        // Scan for vanilla workstation POIs within the village
        scanVanillaProfessions(level, village, result);

        // MCA professions: Guard and Archer are always available
        scanMcaProfessions(result);

        // Townstead professions: Cook and Barista based on building slots
        scanTownsteadProfessions(level, village, result, slotInfo);

        // Remove professions suppressed by Townstead (e.g. Chef's Delight when Townstead cook is active)
        filterSuppressedProfessions(result);

        return buildResult(result, slotInfo);
    }

    private static ScanResult buildResult(Set<String> result, Map<String, int[]> slotInfo) {
        ArrayList<String> sorted = new ArrayList<>(result);
        sorted.sort((a, b) -> {
            if ("minecraft:none".equals(a)) return -1;
            if ("minecraft:none".equals(b)) return 1;
            return a.compareTo(b);
        });
        List<Integer> used = new ArrayList<>();
        List<Integer> max = new ArrayList<>();
        for (String id : sorted) {
            int[] info = slotInfo.get(id);
            used.add(info != null ? info[0] : 0);
            max.add(info != null ? info[1] : -1);
        }
        return new ScanResult(sorted, used, max);
    }

    private static final Map<String, String> BUILDING_TYPE_TO_PROFESSION = Map.ofEntries(
            Map.entry("butcher", "minecraft:butcher"),
            Map.entry("library", "minecraft:librarian"),
            Map.entry("blacksmith", "minecraft:armorer"),
            Map.entry("forge", "minecraft:armorer"),
            Map.entry("fisher", "minecraft:fisherman"),
            Map.entry("farm", "minecraft:farmer"),
            Map.entry("fletcher", "minecraft:fletcher"),
            Map.entry("cartographer", "minecraft:cartographer"),
            Map.entry("mason", "minecraft:mason"),
            Map.entry("shepherd", "minecraft:shepherd"),
            Map.entry("tannery", "minecraft:leatherworker"),
            Map.entry("temple", "minecraft:cleric"),
            Map.entry("church", "minecraft:cleric"),
            Map.entry("tool_smith", "minecraft:toolsmith"),
            Map.entry("weapon_smith", "minecraft:weaponsmith")
    );

    private static void scanVanillaProfessions(ServerLevel level, Village village, Set<String> result) {
        // Map building types to professions — only intentional buildings count,
        // not incidental workstation blocks in unrelated buildings
        Set<String> buildingTypes = new HashSet<>();
        for (var building : village.getBuildings().values()) {
            buildingTypes.add(building.getType());
        }

        for (String buildingType : buildingTypes) {
            String profId = BUILDING_TYPE_TO_PROFESSION.get(buildingType);
            if (profId != null) {
                result.add(profId);
            }
        }

        // Also include professions that current village residents already hold
        // (covers edge cases and modded professions)
        for (var entity : level.getAllEntities()) {
            if (entity instanceof VillagerEntityMCA villager && village.isWithinBorder(villager)) {
                VillagerProfession prof = villager.getVillagerData().getProfession();
                if (prof != VillagerProfession.NONE) {
                    result.add(professionKey(prof));
                }
            }
        }
    }

    private static void scanMcaProfessions(Set<String> result) {
        // Guard and Archer are always available (no workstation needed)
        //? if neoforge {
        addIfRegistered(result, "mca", "guard");
        addIfRegistered(result, "mca", "archer");
        //?} else {
        /*addIfRegistered(result, "mca", "guard");
        addIfRegistered(result, "mca", "archer");
        *///?}
    }

    private static void scanTownsteadProfessions(ServerLevel level, Village village, Set<String> result,
            Map<String, int[]> slotInfo) {
        if (!ModCompat.isLoaded("farmersdelight")) return;

        // Cook: available if any kitchen building exists in the village
        boolean hasKitchen = village.getBuildings().values().stream()
                .anyMatch(b -> b.getType().startsWith("compat/farmersdelight/kitchen_l"));
        if (hasKitchen) {
            String cookKey = professionKey(Townstead.COOK_PROFESSION.get());
            result.add(cookKey);
            int maxCookSlots = FarmersDelightCookAssignment.totalCookSlots(village);
            int activeCooks = 0;
            for (VillagerEntityMCA resident : village.getResidents(level)) {
                if (FarmersDelightCookAssignment.isExternalCookProfession(resident.getVillagerData().getProfession())) {
                    activeCooks++;
                }
            }
            slotInfo.put(cookKey, new int[]{activeCooks, maxCookSlots});
        }

        // Barista: available if any cafe building exists and rusticdelight is loaded
        if (ModCompat.isLoaded("rusticdelight")) {
            boolean hasCafe = village.getBuildings().values().stream()
                    .anyMatch(b -> b.getType().startsWith("compat/rusticdelight/cafe_l"));
            if (hasCafe) {
                String baristaKey = professionKey(Townstead.BARISTA_PROFESSION.get());
                result.add(baristaKey);
                int maxBaristaSlots = FarmersDelightBaristaAssignment.totalCafeSlots(village);
                int activeBaristas = 0;
                for (VillagerEntityMCA resident : village.getResidents(level)) {
                    if (FarmersDelightBaristaAssignment.isBaristaProfession(resident.getVillagerData().getProfession())) {
                        activeBaristas++;
                    }
                }
                slotInfo.put(baristaKey, new int[]{activeBaristas, maxBaristaSlots});
            }
        }
    }

    private static Optional<Village> findPlayerVillage(ServerPlayer player) {
        Optional<Village> nearest = Village.findNearest(player);
        if (nearest.isPresent() && nearest.get().isWithinBorder(player)) return nearest;
        return Optional.empty();
    }

    private static String professionKey(VillagerProfession profession) {
        ResourceLocation key = BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession);
        return key != null ? key.toString() : "minecraft:none";
    }

    private static void filterSuppressedProfessions(Set<String> result) {
        // When Townstead cook mode is active, suppress Chef's Delight professions
        // (Townstead provides its own cook/barista that integrate with the kitchen system)
        if (TownsteadConfig.isTownsteadCookEnabled()) {
            result.remove("chefsdelight:chef");
            result.remove("chefsdelight:cook");
        }
    }

    private static void addIfRegistered(Set<String> result, String namespace, String path) {
        //? if >=1.21 {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(namespace, path);
        //?} else {
        /*ResourceLocation id = new ResourceLocation(namespace, path);
        *///?}
        if (BuiltInRegistries.VILLAGER_PROFESSION.containsKey(id)) {
            result.add(id.toString());
        }
    }
}
