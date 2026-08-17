package com.aetherianartificer.townstead.compat.mca;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.client.catalog.CatalogDataLoader;
import net.conczin.mca.resources.BuildingTypes;
import net.conczin.mca.resources.data.BuildingType;
import net.conczin.mca.server.world.data.Building;
//? if >=1.21 {
import net.conczin.mca.server.world.data.BuildingScanResult;
import net.conczin.mca.server.world.data.RegisteredRoomUpdate;
//?}
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Debounced bridge from Townstead building-signature block changes to MCA's room scanner.
 * Geometry, matching, and persistence remain entirely MCA-owned.
 */
public final class McaBuildingDiscovery {
    private static final int DEBOUNCE_TICKS = 30;
    private static final int REGION_SHIFT = 3; // one pending scan per 8x8x8 local region
    private static final Map<MinecraftServer, Map<PendingKey, Pending>> PENDING = new WeakHashMap<>();
    private static final Map<Block, Boolean> RELEVANT_BLOCKS = new ConcurrentHashMap<>();

    private McaBuildingDiscovery() {}

    public static void onBlockChanged(
            ServerLevel level,
            BlockPos pos,
            @Nullable BlockState oldState,
            @Nullable BlockState newState) {
        if (level == null || pos == null || !TownsteadConfig.ENABLE_MCA_BUILDING_DISCOVERY.get()) return;
        // The analyze/commit room APIs are a floor-system capability. Older MCA keeps its own POI
        // report behaviour until an equivalent transactional update API is available there.
        if (!McaFloorCompat.hasFloorSystem()) return;
        if (!isRelevant(oldState) && !isRelevant(newState)) return;

        PendingKey key = new PendingKey(level.dimension().location(),
                pos.getX() >> REGION_SHIFT, pos.getY() >> REGION_SHIFT, pos.getZ() >> REGION_SHIFT);
        synchronized (PENDING) {
            PENDING.computeIfAbsent(level.getServer(), ignored -> new ConcurrentHashMap<>())
                    .put(key, new Pending(level, pos.immutable(), level.getGameTime() + DEBOUNCE_TICKS));
        }
    }

    public static void tick(MinecraftServer server) {
        if (server == null || !TownsteadConfig.ENABLE_MCA_BUILDING_DISCOVERY.get()) return;
        Map<PendingKey, Pending> pending;
        synchronized (PENDING) {
            pending = PENDING.get(server);
            if (pending == null || pending.isEmpty()) return;
        }
        for (Map.Entry<PendingKey, Pending> entry : pending.entrySet()) {
            Pending task = entry.getValue();
            if (task.level().getGameTime() < task.dueTick()) continue;
            if (!pending.remove(entry.getKey(), task)) continue;
            process(task.level(), task.source());
        }
    }

    public static String pendingDescription(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) return "unavailable";
        PendingKey key = new PendingKey(level.dimension().location(),
                pos.getX() >> REGION_SHIFT, pos.getY() >> REGION_SHIFT, pos.getZ() >> REGION_SHIFT);
        synchronized (PENDING) {
            Map<PendingKey, Pending> pending = PENDING.get(level.getServer());
            Pending task = pending == null ? null : pending.get(key);
            if (task == null) return "no pending scan";
            return "pending in " + Math.max(0L, task.dueTick() - level.getGameTime()) + " ticks from "
                    + task.source();
        }
    }

    private static boolean isRelevant(@Nullable BlockState state) {
        if (state == null || state.isAir()) return false;
        return RELEVANT_BLOCKS.computeIfAbsent(state.getBlock(), ignored -> computeRelevant(state));
    }

    private static boolean computeRelevant(BlockState state) {
        for (BuildingType type : BuildingTypes.getInstance()) {
            if (!type.visible() || CatalogDataLoader.matchGroup(type.name()).isEmpty()) continue;
            for (ResourceLocation requirement : type.getGroups().keySet()) {
                if (BuiltInRegistries.BLOCK.containsKey(requirement)) {
                    if (state.is(BuiltInRegistries.BLOCK.get(requirement))) return true;
                } else if (state.is(TagKey.create(Registries.BLOCK, requirement))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Datapack reload invalidation for block/tag membership and active catalog families. */
    public static void invalidateSignatures() {
        RELEVANT_BLOCKS.clear();
    }

    private static void process(ServerLevel level, BlockPos source) {
        //? if >=1.21 {
        VillageManager manager = VillageManager.get(level);
        Village village = manager.findNearestVillage(source, Village.MERGE_MARGIN).orElse(null);
        if (village != null && !village.isAutoScan()) return;

        Building room = village == null ? null : McaBuildingCompat.functionalRoomAt(level, village, source);
        Building.validationResult result;
        if (room != null) {
            RegisteredRoomUpdate update = manager.analyzeRegisteredRoomUpdate(village, room.getId(), source);
            if (update.result() != Building.validationResult.SUCCESS || update.isAmbiguous()) {
                diagnostic("update", source, update.result(), update.playerMatchingTypes());
                return;
            }
            result = manager.commitRegisteredRoomUpdate(update, null);
        } else if (village != null) {
            BuildingScanResult addition = manager.analyzeRoom(source);
            if (addition.result() == Building.validationResult.SUCCESS) {
                if (addition.isAmbiguous()) {
                    diagnostic("add-room", source, addition.result(), addition.matchingTypes());
                    return;
                }
                result = manager.commitRoomAddition(addition, null);
            } else if (addition.result() == Building.validationResult.NOT_IN_BUILDING) {
                result = manager.processBuilding(source);
            } else {
                diagnostic("add-room", source, addition.result(), addition.matchingTypes());
                return;
            }
        } else {
            result = manager.processBuilding(source);
        }

        if (result != Building.validationResult.SUCCESS) {
            diagnostic("commit", source, result, java.util.List.of());
            return;
        }
        BuildingReportReconciler.reconcileNearest(level, source, false, Townstead.LOGGER);
        //?}
    }

    private static void diagnostic(
            String operation, BlockPos source, Building.validationResult result, java.util.List<String> candidates) {
        if (TownsteadConfig.DEBUG_LOGGING.get()) {
            Townstead.LOGGER.info("MCA building discovery {} at {}: result={} candidates={}",
                    operation, source, result, candidates);
        }
    }

    private record PendingKey(ResourceLocation dimension, int regionX, int regionY, int regionZ) {}
    private record Pending(ServerLevel level, BlockPos source, long dueTick) {}
}
