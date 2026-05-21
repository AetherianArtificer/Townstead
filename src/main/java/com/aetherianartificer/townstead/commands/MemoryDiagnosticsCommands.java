package com.aetherianartificer.townstead.commands;

import com.aetherianartificer.townstead.memory.TownsteadMemoryLifecycle;
import com.aetherianartificer.townstead.storage.VillageAiBudget;
import com.aetherianartificer.townstead.village.TownsteadVillageMigration;
import com.aetherianartificer.townstead.village.TownsteadVillageSavedData;
import com.mojang.brigadier.CommandDispatcher;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * Admin-facing memory diagnostics for large-village troubleshooting.
 */
public final class MemoryDiagnosticsCommands {
    private MemoryDiagnosticsCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext ctx) {
        dispatcher.register(Commands.literal("townstead")
                .then(Commands.literal("memory")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("report").executes(c -> report(c.getSource())))
                        .then(Commands.literal("migrate-now").executes(c -> migrateNow(c.getSource())))
                        .then(Commands.literal("purge-caches").executes(c -> purgeCaches(c.getSource())))));
    }

    private static int report(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        VillageStats villageStats = scanVillages(server);
        TownsteadVillageSavedData savedData = TownsteadVillageSavedData.get(server);
        TownsteadMemoryLifecycle.Snapshot memory = TownsteadMemoryLifecycle.snapshot();
        VillageAiBudget.Snapshot budget = VillageAiBudget.snapshot();

        source.sendSuccess(() -> Component.literal(
                "Townstead memory: mcaVillages=" + villageStats.villages
                        + ", mcaBuildings=" + villageStats.buildings
                        + ", mcaTrackedBlockRefs=" + villageStats.blockRefs
                        + ", townsteadSchema=" + savedData.loadedSchemaVersion() + "/" + TownsteadVillageSavedData.SCHEMA_VERSION
                        + ", migrationComplete=" + savedData.schemaMigrationComplete()
                        + ", townsteadVillageRecords=" + savedData.recordCount()
                        + ", townsteadOverlays=" + savedData.overlayCount()
                        + ", townsteadPackedPositions=" + savedData.trackedPositionCount()
                        + ", caches[target=" + memory.targetReachability()
                        + ", nearbyStorage=" + memory.nearbyStorageSnapshots()
                        + ", villageStorage=" + memory.villageStorageSnapshots()
                        + ", kitchenStorage=" + memory.kitchenStorageSnapshots()
                        + ", dockScan=" + memory.dockScanCache()
                        + ", indexedDocks=" + memory.dockIndexedDocks()
                        + ", berthGroups=" + memory.dockBerthGroups()
                        + ", emergencyBeds=" + memory.emergencyBedClaims()
                        + ", producerStations=" + memory.producerStationClaims()
                        + ", villagerStates=" + memory.villagerStates()
                        + ", dirtyVillagerStates=" + memory.dirtyVillagerStates()
                        + ", aiScopes=" + memory.aiBudgetScopes()
                        + "]"
                        + ", aiBudget[granted=" + budget.granted()
                        + ", throttled=" + budget.throttled() + "]"),
                false);
        return 1;
    }

    private static int migrateNow(CommandSourceStack source) {
        TownsteadVillageMigration.Result result = TownsteadVillageMigration.migrateServer(source.getServer());
        source.sendSuccess(() -> Component.literal(
                "Townstead migration scanned " + result.villagesScanned()
                        + " villages and migrated/compacted "
                        + result.buildingsMigrated() + " synthetic buildings."),
                true);
        return 1;
    }

    private static int purgeCaches(CommandSourceStack source) {
        TownsteadMemoryLifecycle.clearAll();
        source.sendSuccess(() -> Component.literal("Townstead runtime caches cleared."), true);
        return 1;
    }

    private static VillageStats scanVillages(MinecraftServer server) {
        int villages = 0;
        int buildings = 0;
        int blockRefs = 0;
        for (ServerLevel level : server.getAllLevels()) {
            VillageManager manager = VillageManager.get(level);
            for (Village village : manager) {
                villages++;
                buildings += village.getBuildings().size();
                for (Building building : village.getBuildings().values()) {
                    blockRefs += countBlockRefs(building);
                }
            }
        }
        return new VillageStats(villages, buildings, blockRefs);
    }

    private static int countBlockRefs(Building building) {
        int count = 0;
        for (BlockPos ignored : (Iterable<BlockPos>) building.getBlockPosStream()::iterator) count++;
        return count;
    }

    private record VillageStats(int villages, int buildings, int blockRefs) {}
}
