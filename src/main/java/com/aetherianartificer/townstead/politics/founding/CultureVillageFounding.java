package com.aetherianartificer.townstead.politics.founding;

import com.aetherianartificer.townstead.culture.CultureVillages;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Culture villages that generate in the world arrive empty: their pieces carry no villagers, and MCA
 * only makes a village where villagers already are. So when one generates, Townstead founds it the
 * way the debug command does, from the founding profile its structure belongs to, and spawns one
 * resident per home. It waits until every chunk the structure covers has loaded, since only then are
 * all its buildings placed.
 *
 * <p>This is the one automatic path allowed to start an MCA village where none exists, and only for
 * a culture's own village structure.</p>
 */
public final class CultureVillageFounding {
    private static final Logger LOGGER = LoggerFactory.getLogger("Townstead/CultureVillageFounding");
    private static final int INTERVAL = 40;

    private record Pending(ResourceLocation structure, BoundingBox box) {}

    /** Structure starts waiting for their chunks, by level, keyed by the start's chunk. */
    private static final Map<ServerLevel, Map<Long, Pending>> PENDING = new java.util.WeakHashMap<>();
    /** Starts already handled this session, founded or not, so a failure is not retried every load. */
    private static final Map<ServerLevel, Set<Long>> HANDLED = new java.util.WeakHashMap<>();

    private CultureVillageFounding() {}

    /** Notes any culture village whose structure starts in this freshly loaded chunk. */
    public static void onChunkLoad(ServerLevel level, ChunkAccess chunk) {
        if (chunk.getAllStarts().isEmpty()) return;
        var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        for (var entry : chunk.getAllStarts().entrySet()) {
            var start = entry.getValue();
            if (start == null || !start.isValid()) continue;
            ResourceLocation id = registry.getKey(entry.getKey());
            if (id == null || CultureVillages.culture(id) == null) continue;
            long key = start.getChunkPos().toLong();
            if (HANDLED.computeIfAbsent(level, k -> new HashSet<>()).contains(key)) continue;
            PENDING.computeIfAbsent(level, k -> new HashMap<>()).putIfAbsent(key, new Pending(id, start.getBoundingBox()));
        }
    }

    public static void tick(MinecraftServer server) {
        if (PENDING.isEmpty() || server.getTickCount() % INTERVAL != 0) return;
        for (var byLevel = PENDING.entrySet().iterator(); byLevel.hasNext(); ) {
            var levelEntry = byLevel.next();
            ServerLevel level = levelEntry.getKey();
            for (Iterator<Map.Entry<Long, Pending>> it = levelEntry.getValue().entrySet().iterator(); it.hasNext(); ) {
                var entry = it.next();
                BoundingBox box = entry.getValue().box();
                if (!level.hasChunksAt(box.minX(), box.minZ(), box.maxX(), box.maxZ())) continue;
                it.remove();
                HANDLED.computeIfAbsent(level, k -> new HashSet<>()).add(entry.getKey());
                try {
                    found(level, entry.getValue());
                } catch (RuntimeException exception) {
                    LOGGER.warn("Could not found culture village {} at {}", entry.getValue().structure(), box.getCenter(), exception);
                }
            }
            if (levelEntry.getValue().isEmpty()) byLevel.remove();
        }
    }

    private static void found(ServerLevel level, Pending pending) {
        BlockPos center = pending.box().getCenter();
        BlockPos anchor = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, center);
        VillageManager villages = VillageManager.get(level);
        // Already a village here: founded before, by a player, or by the debug command.
        if (villages.findNearestVillage(anchor, Village.MERGE_MARGIN).isPresent()) return;

        DebugVillageSpawner.refreshPoiData(level, anchor);
        BoundingBox reach = pending.box().inflatedBy(8);
        List<BlockPos> homes = DebugVillageSpawner.homes(level, anchor).filter(reach::isInside).toList();
        if (homes.isEmpty()) {
            LOGGER.info("Culture village {} at {} has no beds; not founded", pending.structure(), anchor);
            return;
        }
        int recognized = 0;
        for (BlockPos home : homes) {
            if (villages.processBuilding(home) == net.conczin.mca.server.world.data.Building.validationResult.SUCCESS) recognized++;
        }
        // The buildings MCA took may not reach the structure's centre, so look as far as the debug command does.
        Village village = recognized == 0 ? null : villages.findNearestVillage(anchor, Village.MERGE_MARGIN)
                .or(() -> villages.findNearestVillage(anchor, DebugVillageSpawner.VILLAGE_RADIUS)).orElse(null);
        if (village == null) {
            LOGGER.info("MCA did not recognize culture village {} at {} ({} of {} homes recognized)",
                    pending.structure(), anchor, recognized, homes.size());
            return;
        }
        FoundingProfileDefinition profile = FoundingSelection.choose(level, village);
        if (profile == null) {
            // Never leave an empty village behind (a culture switched off, say).
            villages.removeVillage(village.getId());
            LOGGER.info("No founding profile fits culture village {} at {}; left unfounded", pending.structure(), anchor);
            return;
        }
        FoundingProfileApplier.apply(level, village, profile, anchor);
        int residents = DebugVillageSpawner.spawnResidents(level, profile, homes, homes.size());
        // Again now that residents exist to fill the government's offices.
        FoundingProfileApplier.apply(level, village, profile, anchor);
        LOGGER.info("Founded {} ({}) at {} from {} with {} residents",
                village.getName(), profile.id(), anchor, pending.structure(), residents);
    }

    public static void clear() {
        PENDING.clear();
        HANDLED.clear();
    }
}
