package com.aetherianartificer.townstead.profession;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.compat.ModCompat;
import com.aetherianartificer.townstead.compat.farmersdelight.FarmersDelightCookAssignment;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.VillagerProfession;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Server-side logic to determine which professions are available in a village.
 */
public final class ProfessionScanner {

    private ProfessionScanner() {}

    /**
     * Scans the village the player is in and returns a list of available profession
     * ResourceLocation strings, sorted for display.
     */
    public static List<String> scanAvailableProfessions(ServerPlayer player) {
        Set<String> result = new LinkedHashSet<>();

        // Always include NONE (strip profession)
        result.add(professionKey(VillagerProfession.NONE));

        // Find the village the player is in
        Optional<Village> villageOpt = findPlayerVillage(player);
        if (villageOpt.isEmpty()) return new ArrayList<>(result);
        Village village = villageOpt.get();

        ServerLevel level = player.serverLevel();

        // Scan for vanilla workstation POIs within the village
        scanVanillaProfessions(level, village, result);

        // MCA professions: Guard and Archer are always available
        scanMcaProfessions(result);

        // Townstead professions: Cook and Barista based on building slots
        scanTownsteadProfessions(level, village, result);

        return new ArrayList<>(result);
    }

    private static void scanVanillaProfessions(ServerLevel level, Village village, Set<String> result) {
        BlockPos center = BlockPos.containing(village.getCenter().getX(),
                village.getCenter().getY(), village.getCenter().getZ());
        // Use the larger of the village's X or Z span as the scan radius, plus margin
        int radius = Math.max(village.getBox().getXSpan(), village.getBox().getZSpan()) / 2 + 16;

        PoiManager poiManager = level.getPoiManager();

        // For each registered profession, check if any matching workstation POI exists
        for (var entry : BuiltInRegistries.VILLAGER_PROFESSION.entrySet()) {
            VillagerProfession profession = entry.getValue();
            // Skip NONE (already added) and professions with no workstation requirement
            if (profession == VillagerProfession.NONE) continue;
            if (profession.heldJobSite() == PoiType.NONE) continue;

            // Check if any POI matching this profession's heldJobSite exists in the village area
            long count = poiManager.getCountInRange(
                    profession.heldJobSite(), center, radius, PoiManager.Occupancy.ANY);
            if (count > 0) {
                result.add(professionKey(profession));
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

    private static void scanTownsteadProfessions(ServerLevel level, Village village, Set<String> result) {
        if (!ModCompat.isLoaded("farmersdelight")) return;

        // Cook: available if any kitchen building exists in the village
        boolean hasKitchen = village.getBuildings().values().stream()
                .anyMatch(b -> b.getType().startsWith("compat/farmersdelight/kitchen_l"));
        if (hasKitchen) {
            String cookKey = professionKey(Townstead.COOK_PROFESSION.get());
            result.add(cookKey);
        }

        // Barista: available if any cafe building exists and rusticdelight is loaded
        if (ModCompat.isLoaded("rusticdelight")) {
            boolean hasCafe = village.getBuildings().values().stream()
                    .anyMatch(b -> b.getType().startsWith("compat/rusticdelight/cafe_l"));
            if (hasCafe) {
                String baristaKey = professionKey(Townstead.BARISTA_PROFESSION.get());
                result.add(baristaKey);
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
