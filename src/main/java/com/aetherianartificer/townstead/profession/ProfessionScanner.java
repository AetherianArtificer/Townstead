package com.aetherianartificer.townstead.profession;

import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.compat.ModCompat;
import com.aetherianartificer.townstead.compat.farmersdelight.FarmersDelightBaristaAssignment;
import com.aetherianartificer.townstead.compat.farmersdelight.FarmersDelightCookAssignment;
import com.aetherianartificer.townstead.profession.ProfessionSlotRules.SlotPolicy;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
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

        // Remove professions suppressed by Townstead (e.g. Chef's Delight when Townstead cook is active)
        filterSuppressedProfessions(result);
        filterSuppressedProfessions(slotInfo.keySet());

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

    private static void scanVanillaProfessions(ServerLevel level, Village village, Set<String> result, Map<String, int[]> slotInfo) {
        refreshVillagePoiData(level, village);
        Map<String, Integer> activeCounts = countResidentProfessions(level, village);

        for (VillagerProfession profession : BuiltInRegistries.VILLAGER_PROFESSION) {
            if (profession == null || profession == VillagerProfession.NONE) continue;

            String professionId = professionKey(profession);
            int activeResidents = activeCounts.getOrDefault(professionId, 0);

            switch (ProfessionSlotRules.classify(profession)) {
                case POI_LIMITED -> {
                    releaseStaleJobSites(level, village, profession);
                    int maxSlots = countVillagePoiSlots(level, village, profession);
                    if (maxSlots > 0 || activeResidents > 0) {
                        result.add(professionId);
                        slotInfo.put(professionId, new int[]{activeResidents, maxSlots});
                    }
                }
                case CUSTOM_BUILDING_SLOTS -> {
                    int[] info = customSlotInfo(level, village, profession);
                    if (info[1] > 0 || info[0] > 0) {
                        result.add(professionId);
                        slotInfo.put(professionId, info);
                    }
                }
                case UNLIMITED -> {
                    if (ProfessionSlotRules.isAlwaysVisible(profession) || activeResidents > 0) {
                        result.add(professionId);
                        slotInfo.put(professionId, new int[]{activeResidents, -1});
                    }
                }
            }
        }
    }

    private static Optional<Village> findPlayerVillage(ServerPlayer player) {
        Optional<Village> nearest = Village.findNearest(player);
        if (nearest.isPresent() && nearest.get().isWithinBorder(player)) return nearest;
        return Optional.empty();
    }

    private static void filterSuppressedProfessions(Set<String> result) {
        // When Townstead cook mode is active, suppress Chef's Delight professions
        // (Townstead provides its own cook/barista that integrate with the kitchen system)
        if (TownsteadConfig.isTownsteadCookEnabled()) {
            result.remove("chefsdelight:chef");
            result.remove("chefsdelight:cook");
        }
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

    private static Map<String, Integer> countResidentProfessions(ServerLevel level, Village village) {
        Map<String, Integer> counts = new HashMap<>();
        Set<UUID> seen = new HashSet<>();
        for (var entity : level.getAllEntities()) {
            if (!(entity instanceof VillagerEntityMCA resident)) continue;
            if (!resident.isAlive() || !village.isWithinBorder(resident)) continue;
            if (!seen.add(resident.getUUID())) continue;
            String professionId = professionKey(resident.getVillagerData().getProfession());
            counts.merge(professionId, 1, Integer::sum);
        }
        return counts;
    }

    private static int[] customSlotInfo(ServerLevel level, Village village, VillagerProfession profession) {
        String professionId = professionKey(profession);
        if ("townstead:cook".equals(professionId)) {
            if (!ModCompat.isLoaded("farmersdelight")) return new int[]{0, 0};
            int activeCooks = 0;
            for (VillagerEntityMCA resident : village.getResidents(level)) {
                if (FarmersDelightCookAssignment.isExternalCookProfession(resident.getVillagerData().getProfession())) {
                    activeCooks++;
                }
            }
            return new int[]{activeCooks, FarmersDelightCookAssignment.totalCookSlots(village)};
        }
        if ("townstead:barista".equals(professionId)) {
            if (!ModCompat.isLoaded("rusticdelight")) return new int[]{0, 0};
            int activeBaristas = 0;
            for (VillagerEntityMCA resident : village.getResidents(level)) {
                if (FarmersDelightBaristaAssignment.isBaristaProfession(resident.getVillagerData().getProfession())) {
                    activeBaristas++;
                }
            }
            return new int[]{activeBaristas, FarmersDelightBaristaAssignment.totalCafeSlots(village)};
        }
        return new int[]{0, 0};
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

    private static String professionKey(VillagerProfession profession) {
        return ProfessionSlotRules.professionKey(profession);
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
