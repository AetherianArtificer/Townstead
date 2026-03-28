package com.aetherianartificer.townstead.profession;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.compat.ModCompat;
import com.aetherianartificer.townstead.compat.farmersdelight.FarmersDelightBaristaAssignment;
import com.aetherianartificer.townstead.compat.farmersdelight.FarmersDelightCookAssignment;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

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
        scanVanillaProfessions(level, village, result, slotInfo);

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

    private static void scanVanillaProfessions(ServerLevel level, Village village, Set<String> result, Map<String, int[]> slotInfo) {
        refreshVillagePoiData(level, village);

        // Map building types to professions — only intentional buildings count,
        // not incidental workstation blocks in unrelated buildings
        Set<String> buildingTypes = new HashSet<>();
        for (var building : village.getBuildings().values()) {
            buildingTypes.add(building.getType());
        }

        for (String buildingType : buildingTypes) {
            String profId = isFarmBuildingType(buildingType)
                    ? "minecraft:farmer"
                    : BUILDING_TYPE_TO_PROFESSION.get(buildingType);
            if (profId != null) {
                result.add(profId);
            }
        }

        releaseStaleJobSites(level, village, VillagerProfession.FARMER);

        String farmerKey = professionKey(VillagerProfession.FARMER);
        int farmerMaxSlots = countVillagePoiSlots(level, village, VillagerProfession.FARMER);
        if (farmerMaxSlots > 0) {
            result.add(farmerKey);
            slotInfo.put(farmerKey, new int[]{countProfessionResidents(level, village, VillagerProfession.FARMER), farmerMaxSlots});
        }

        // Also include professions that current village residents already hold
        // (covers edge cases and modded professions)
        for (var entity : level.getAllEntities()) {
            if (entity instanceof VillagerEntityMCA villager && villager.isAlive() && village.isWithinBorder(villager)) {
                VillagerProfession prof = villager.getVillagerData().getProfession();
                if (prof != VillagerProfession.NONE) {
                    result.add(professionKey(prof));
                }
            }
        }

        int activeFarmers = countProfessionResidents(level, village, VillagerProfession.FARMER);
        if (activeFarmers > 0) {
            slotInfo.put(farmerKey, new int[]{activeFarmers, Math.max(farmerMaxSlots, 0)});
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

    private static boolean isFarmBuildingType(String buildingType) {
        if (buildingType == null || buildingType.isBlank()) return false;
        String normalized = buildingType.toLowerCase(Locale.ROOT);
        return normalized.equals("farm")
                || normalized.startsWith("farm")
                || normalized.contains("/farm")
                || normalized.contains("_farm");
    }

    private static int countVillagePoiSlots(ServerLevel level, Village village, VillagerProfession profession) {
        if (level == null || village == null || profession == null || profession == VillagerProfession.NONE) return 0;
        BlockPos center = new BlockPos(village.getCenter());
        PoiManager poiManager = level.getPoiManager();
        return (int) poiManager.findAll(
                profession.heldJobSite(),
                pos -> village.isWithinBorder(pos, Village.BORDER_MARGIN),
                center,
                128,
                PoiManager.Occupancy.ANY
        ).count();
    }

    private static int countProfessionResidents(ServerLevel level, Village village, VillagerProfession profession) {
        Set<UUID> seen = new HashSet<>();
        int count = 0;
        for (var entity : level.getAllEntities()) {
            if (!(entity instanceof VillagerEntityMCA resident)) continue;
            if (!resident.isAlive() || !village.isWithinBorder(resident)) continue;
            if (!seen.add(resident.getUUID())) continue;
            if (resident.getVillagerData().getProfession() == profession) {
                count++;
            }
        }
        return count;
    }

    private static void releaseStaleJobSites(ServerLevel level, Village village, VillagerProfession profession) {
        if (profession == null || profession == VillagerProfession.NONE) return;

        PoiManager poiManager = level.getPoiManager();
        Set<BlockPos> liveClaims = new HashSet<>();
        for (var entity : level.getAllEntities()) {
            if (!(entity instanceof VillagerEntityMCA resident)) continue;
            if (!resident.isAlive() || !village.isWithinBorder(resident)) continue;
            if (!professionOwnsJobSite(resident.getVillagerData().getProfession(), profession)) continue;
            resident.getBrain().getMemory(MemoryModuleType.JOB_SITE)
                    .filter(globalPos -> globalPos.dimension().equals(level.dimension()))
                    .map(GlobalPos::pos)
                    .ifPresent(liveClaims::add);
        }

        poiManager.findAll(
                profession.heldJobSite(),
                pos -> village.isWithinBorder(pos, Village.BORDER_MARGIN),
                new BlockPos(village.getCenter()),
                128,
                PoiManager.Occupancy.ANY
        ).forEach(pos -> {
            if (liveClaims.contains(pos)) return;
            if (poiManager.getFreeTickets(pos) > 0) return;
            poiManager.release(pos);
        });
    }

    private static boolean professionOwnsJobSite(VillagerProfession holderProfession, VillagerProfession targetProfession) {
        if (holderProfession == null || targetProfession == null) return false;
        if (holderProfession == targetProfession) return true;
        return holderProfession.heldJobSite().equals(targetProfession.heldJobSite());
    }

    private static void refreshVillagePoiData(ServerLevel level, Village village) {
        if (level == null || village == null) return;

        BlockPos center = new BlockPos(village.getCenter());
        int chunkRadius = Math.floorDiv(128, 16) + 1;
        SectionPos.aroundChunk(
                new ChunkPos(center),
                chunkRadius,
                level.getMinSection(),
                level.getMaxSection()
        ).forEach(sectionPos -> {
            LevelChunk chunk = level.getChunk(sectionPos.x(), sectionPos.z());
            int sectionIndex = level.getSectionIndexFromSectionY(sectionPos.y());
            if (sectionIndex < 0 || sectionIndex >= chunk.getSections().length) return;

            LevelChunkSection chunkSection = chunk.getSection(sectionIndex);
            if (chunkSection == null) return;

            level.getPoiManager().checkConsistencyWithBlocks(sectionPos, chunkSection);
        });
    }
}
