package com.aetherianartificer.townstead.commands;

import com.aetherianartificer.townstead.memory.TownsteadMemoryLifecycle;
import com.aetherianartificer.townstead.diagnostics.TownsteadProfiler;
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
                        .then(Commands.literal("profile")
                                .then(Commands.literal("start").executes(c -> profileStart(c.getSource())))
                                .then(Commands.literal("stop").executes(c -> profileStop(c.getSource())))
                                .then(Commands.literal("reset").executes(c -> profileReset(c.getSource())))
                                .then(Commands.literal("report").executes(c -> profileReport(c.getSource()))))
                        .then(Commands.literal("migrate-now").executes(c -> migrateNow(c.getSource())))
                        .then(Commands.literal("purge-caches").executes(c -> purgeCaches(c.getSource())))));
    }

    private static int report(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        VillageStats villageStats = scanVillages(server);
        TownsteadVillageSavedData savedData = TownsteadVillageSavedData.get(server);
        TownsteadMemoryLifecycle.Snapshot memory = TownsteadMemoryLifecycle.snapshot();
        VillageAiBudget.Snapshot budget = VillageAiBudget.snapshot();

        source.sendSuccess(() -> Component.translatable("command.townstead.memory.report",
                        villageStats.villages, villageStats.buildings, villageStats.blockRefs,
                        savedData.loadedSchemaVersion(), TownsteadVillageSavedData.SCHEMA_VERSION,
                        savedData.schemaMigrationComplete(), savedData.recordCount(), savedData.overlayCount(),
                        savedData.trackedPositionCount(), memory.targetReachability(),
                        memory.nearbyStorageSnapshots(), memory.villageStorageSnapshots(),
                        memory.kitchenStorageSnapshots(), memory.dockScanCache(), memory.dockIndexedDocks(),
                        memory.dockBerthGroups(), memory.producerStationClaims(), memory.villagerStates(),
                        memory.dirtyVillagerStates(), memory.aiBudgetScopes(), budget.granted(), budget.throttled()),
                false);
        return 1;
    }

    private static int profileStart(CommandSourceStack source) {
        TownsteadProfiler.clear();
        TownsteadProfiler.setEnabled(true);
        source.sendSuccess(() -> Component.translatable("command.townstead.memory.profiler.started"), true);
        return 1;
    }

    private static int profileStop(CommandSourceStack source) {
        TownsteadProfiler.setEnabled(false);
        source.sendSuccess(() -> Component.translatable("command.townstead.memory.profiler.stopped"), true);
        return 1;
    }

    private static int profileReset(CommandSourceStack source) {
        TownsteadProfiler.clear();
        source.sendSuccess(() -> Component.translatable("command.townstead.memory.profiler.reset"), true);
        return 1;
    }

    private static int profileReport(CommandSourceStack source) {
        TownsteadProfiler.Snapshot snapshot = TownsteadProfiler.snapshot();
        source.sendSuccess(() -> Component.translatable("command.townstead.memory.profiler.heading",
                Component.translatable(snapshot.enabled()
                        ? "command.townstead.memory.profiler.running"
                        : "command.townstead.memory.profiler.inactive")), false);
        int rows = 0;
        for (TownsteadProfiler.Row row : snapshot.rows()) {
            if (rows++ >= 12) break;
            source.sendSuccess(() -> Component.translatable("command.townstead.memory.profiler.row",
                    row.name(), row.calls(),
                    String.format(java.util.Locale.ROOT, "%.3f", row.millis()),
                    String.format(java.util.Locale.ROOT, "%.3f", row.microsPerCall())), false);
        }
        if (rows == 0) {
            source.sendSuccess(() -> Component.translatable(
                    "command.townstead.memory.profiler.no_samples"), false);
        }
        return 1;
    }

    private static int migrateNow(CommandSourceStack source) {
        TownsteadVillageMigration.Result result = TownsteadVillageMigration.migrateServer(source.getServer());
        source.sendSuccess(() -> Component.translatable("command.townstead.memory.migration.complete",
                        result.villagesScanned(), result.buildingsMigrated()),
                true);
        return 1;
    }

    private static int purgeCaches(CommandSourceStack source) {
        TownsteadMemoryLifecycle.clearAll();
        source.sendSuccess(() -> Component.translatable("command.townstead.memory.caches_cleared"), true);
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
                buildings += com.aetherianartificer.townstead.compat.mca.McaBuildings.allById(village).size();
                for (Building building : com.aetherianartificer.townstead.compat.mca.McaBuildings.all(village)) {
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
