package com.aetherianartificer.townstead.village;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.compat.ModCompat;
import com.aetherianartificer.townstead.enclosure.EnclosureTypeIndex;
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
 * <p>Rules, deliberately narrow — a building is removed only when:
 * <ul>
 *   <li>its type is {@code compat/<mod>/...} and that mod is not loaded, or</li>
 *   <li>it is a Townstead-synthesized enclosure (overlay kind {@code enclosure})
 *       whose type is no longer registered in {@link EnclosureTypeIndex}.</li>
 * </ul>
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
        TownsteadVillageSavedData data = TownsteadVillageSavedData.get(level.getServer());
        TownsteadVillageSavedData.VillageRecord record = data.getRecord(level, village.getId());

        List<Integer> toRemove = new ArrayList<>();
        for (Map.Entry<Integer, Building> entry : com.aetherianartificer.townstead.compat.mca.McaBuildings.allById(village).entrySet()) {
            String type = entry.getValue().getType();
            String reason = staleReason(record, entry.getKey(), type);
            if (reason == null) continue;
            toRemove.add(entry.getKey());
            LOG.info("Removing stale building '{}' (id={}) from village {}: {}",
                    type, entry.getKey(), village.getId(), reason);
        }
        for (int id : toRemove) {
            village.removeBuilding(id);
            data.removeBuilding(level, village.getId(), id);
        }
        if (!toRemove.isEmpty()) {
            village.calculateDimensions();
            village.markDirty();
        }
        return toRemove.size();
    }

    /**
     * Removes Townstead-synthesized buildings (landings, pens) whose tracked blocks are all
     * gone from the world. Pre-v2 MCA's grouped validation handled this ({@code
     * validateBuilding} returning TOO_SMALL on rescan); floor-system v2 never validates
     * external buildings, so demolition cleanup is ours on every MCA generation. Deliberately
     * conservative: a building is removed only when its chunks are loaded and ZERO tracked
     * blocks remain in place.
     */
    public static int sweepDemolishedSynthetics(ServerLevel level, Village village) {
        if (level == null || village == null) return 0;
        TownsteadVillageSavedData data = TownsteadVillageSavedData.get(level.getServer());
        TownsteadVillageSavedData.VillageRecord record = data.getRecord(level, village.getId());
        if (record == null) return 0;

        List<Integer> toRemove = new ArrayList<>();
        for (Map.Entry<Integer, Building> entry
                : com.aetherianartificer.townstead.compat.mca.McaBuildings.allById(village).entrySet()) {
            TownsteadVillageSavedData.BuildingOverlay overlay = record.buildings().get((int) entry.getKey());
            if (overlay == null) continue;
            int total = 0;
            int present = 0;
            boolean allLoaded = true;
            for (Map.Entry<String, long[]> byBlock : overlay.blockPositions().entrySet()) {
                net.minecraft.world.level.block.Block expected = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                        .get(net.minecraft.resources.ResourceLocation.tryParse(byBlock.getKey()));
                for (long packed : byBlock.getValue()) {
                    net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.of(packed);
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
            if (present == 0) toRemove.add(entry.getKey());
        }
        for (int id : toRemove) {
            village.removeBuilding(id);
            data.removeBuilding(level, village.getId(), id);
            LOG.info("Removed demolished synthetic building {} from village {}", id, village.getId());
        }
        if (!toRemove.isEmpty()) village.markDirty();
        return toRemove.size();
    }

    private static String staleReason(TownsteadVillageSavedData.VillageRecord record, int buildingId, String type) {
        if (!ModCompat.isCompatAvailable(type)) {
            return "compat mod '" + ModCompat.extractCompatModId(type) + "' is not loaded";
        }
        if (record != null) {
            TownsteadVillageSavedData.BuildingOverlay overlay = record.buildings().get(buildingId);
            if (overlay != null && "enclosure".equals(overlay.kind())
                    && !EnclosureTypeIndex.isEnclosureType(type)) {
                return "enclosure type is no longer registered";
            }
        }
        return null;
    }
}
