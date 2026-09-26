package com.aetherianartificer.townstead.village;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.compat.ModCompat;
import com.aetherianartificer.townstead.client.catalog.CatalogDataLoader;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Server-start sweep that removes village buildings whose type is no longer
 * backed by anything, so stale data self-heals instead of lingering in saved
 * villages (bloating the village snapshot packet, showing phantom buildings
 * on the blueprint map, or shielding them via the open-air validation mixin).
 *
 * <p>Rules, deliberately narrow — a building is removed only when its type is superseded by
 * an installed provider, or is {@code compat/<mod>/...} and that mod is not loaded.
 * Types merely unknown to MCA's BuildingTypes are left alone: a temporarily
 * disabled datapack must not wipe villages.
 */
public final class VillageSanitizer {
    private static final Logger LOG = LoggerFactory.getLogger(Townstead.MOD_ID + "/VillageSanitizer");

    private VillageSanitizer() {}

    public static void sanitizeServer(MinecraftServer server) {
        if (server == null) return;
        int removed = 0;
        for (ServerLevel level : server.getAllLevels()) {
            for (Village village : VillageManager.get(level)) {
                removed += sanitizeVillage(level, village);
            }
        }
        if (removed > 0) {
            LOG.info("Removed {} stale building(s) across all villages", removed);
        }
    }

    public static int sanitizeVillage(ServerLevel level, Village village) {
        if (level == null || village == null) return 0;
        List<Integer> toRemove = new ArrayList<>();
        for (Map.Entry<Integer, Building> entry : com.aetherianartificer.townstead.compat.mca.McaBuildings.allById(village).entrySet()) {
            String type = entry.getValue().getType();
            String reason = staleReason(type);
            if (reason == null) continue;
            toRemove.add(entry.getKey());
            LOG.info("Removing stale building '{}' (id={}) from village {}: {}",
                    type, entry.getKey(), village.getId(), reason);
        }
        for (int id : toRemove) {
            com.aetherianartificer.townstead.compat.mca.McaBuildings.remove(village, id);
        }
        if (!toRemove.isEmpty()) {
            village.calculateDimensions();
            village.markDirty();
        }
        return toRemove.size();
    }

    /**
     * Removes an open-air building whose recorded blocks are all gone from the world. Pre-v2 MCA
     * handled this through {@code validateBuilding}; the floor system never validates external
     * buildings, so demolition cleanup is ours on every MCA generation. Deliberately conservative:
     * a building goes only when its chunks are loaded and not one recorded block remains.
     */
    public static int sweepDemolishedSynthetics(ServerLevel level, Village village) {
        if (level == null || village == null) return 0;
        List<Integer> toRemove = new ArrayList<>();
        for (Building building
                : com.aetherianartificer.townstead.compat.mca.McaBuildings.all(village)) {
            if (!com.aetherianartificer.townstead.compat.mca.McaBuildings
                    .isOpenAirRecord(level, village, building)) continue;
            int total = 0;
            int present = 0;
            boolean allLoaded = true;
            for (Map.Entry<net.minecraft.resources.ResourceLocation, List<net.minecraft.core.BlockPos>> byBlock
                    : building.getBlocks().entrySet()) {
                net.minecraft.world.level.block.Block expected =
                        net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(byBlock.getKey());
                for (net.minecraft.core.BlockPos pos : byBlock.getValue()) {
                    if (!level.isLoaded(pos)) {
                        allLoaded = false;
                        break;
                    }
                    total++;
                    if (level.getBlockState(pos).is(expected)) present++;
                }
                if (!allLoaded) break;
            }
            if (!allLoaded || total == 0) continue;
            if (present == 0) toRemove.add(building.getId());
        }
        for (int id : toRemove) {
            com.aetherianartificer.townstead.compat.mca.McaBuildings.remove(village, id);
            LOG.info("Removed demolished open-air building {} from village {}", id, village.getId());
        }
        if (!toRemove.isEmpty()) village.markDirty();
        return toRemove.size();
    }

    private static String staleReason(String type) {
        if (CatalogDataLoader.isActiveSupersededBuildingType(type)) {
            return "superseded by an installed building provider";
        }
        if (!ModCompat.isCompatAvailable(type)) {
            return "compat mod '" + ModCompat.extractCompatModId(type) + "' is not loaded";
        }
        return null;
    }
}
