package com.aetherianartificer.townstead.memory;

import com.aetherianartificer.townstead.ai.work.producer.ProducerStationClaims;
import com.aetherianartificer.townstead.compat.farmersdelight.cook.KitchenStorageIndex;
import com.aetherianartificer.townstead.dock.DockBerthClaims;
import com.aetherianartificer.townstead.dock.DockLocationIndex;
import com.aetherianartificer.townstead.dock.DockScanner;
import com.aetherianartificer.townstead.dock.DockSuppression;
import com.aetherianartificer.townstead.enclosure.EnclosureSuppression;
import com.aetherianartificer.townstead.fatigue.EmergencyBedClaims;
import com.aetherianartificer.townstead.hunger.NearbyStorageIndex;
import com.aetherianartificer.townstead.hunger.TargetReachabilityCache;
import com.aetherianartificer.townstead.spirit.VillageSpiritCache;
import com.aetherianartificer.townstead.storage.VillageAiBudget;
import com.aetherianartificer.townstead.storage.VillageStorageIndex;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * Central lifecycle hooks for memory-only state.
 */
public final class TownsteadMemoryLifecycle {
    private static final long PURGE_INTERVAL_TICKS = 200L;
    private static final long VILLAGER_STATE_IDLE_TICKS = 20L * 60L * 10L;

    private TownsteadMemoryLifecycle() {}

    public static void tick(MinecraftServer server) {
        if (server == null) return;
        ServerLevel overworld = server.overworld();
        long gameTime = overworld.getGameTime();
        if (gameTime % PURGE_INTERVAL_TICKS != 0L) return;
        purgeExpired(gameTime);
    }

    public static void purgeExpired(long gameTime) {
        TargetReachabilityCache.purgeExpired(gameTime);
        NearbyStorageIndex.purgeExpired(gameTime);
        VillageStorageIndex.purgeExpired(gameTime);
        KitchenStorageIndex.purgeExpired(gameTime);
        DockScanner.purgeExpired(gameTime);
        DockBerthClaims.purgeExpired(gameTime);
        EmergencyBedClaims.purgeExpired(gameTime);
        ProducerStationClaims.purgeExpired(gameTime);
        TownsteadVillagers.purgeExpired(gameTime, VILLAGER_STATE_IDLE_TICKS);
    }

    public static void clearAll() {
        TargetReachabilityCache.clearAll();
        NearbyStorageIndex.clearAll();
        VillageStorageIndex.clearAll();
        KitchenStorageIndex.clearAll();
        DockScanner.clearAll();
        DockLocationIndex.clear();
        DockBerthClaims.clearAll();
        DockSuppression.clearAll();
        EnclosureSuppression.clearAll();
        EmergencyBedClaims.clearAll();
        ProducerStationClaims.clearAll();
        TownsteadVillagers.clearAll();
        VillageAiBudget.clear();
        VillageSpiritCache.clear();
    }

    public record Snapshot(
            int targetReachability,
            int nearbyStorageSnapshots,
            int villageStorageSnapshots,
            int kitchenStorageSnapshots,
            int dockScanCache,
            int dockIndexedVillages,
            int dockIndexedDocks,
            int dockBerthGroups,
            int dockSuppressions,
            int enclosureSuppressions,
            int emergencyBedClaims,
            int producerStationClaims,
            int villagerStates,
            int dirtyVillagerStates,
            int aiBudgetScopes
    ) {}

    public static Snapshot snapshot() {
        return new Snapshot(
                TargetReachabilityCache.size(),
                NearbyStorageIndex.snapshotCount(),
                VillageStorageIndex.snapshotCount(),
                KitchenStorageIndex.snapshotCount(),
                DockScanner.cacheSize(),
                DockLocationIndex.villageCount(),
                DockLocationIndex.dockCount(),
                DockBerthClaims.claimGroupCount(),
                DockSuppression.entryCount(),
                EnclosureSuppression.entryCount(),
                EmergencyBedClaims.size(),
                ProducerStationClaims.size(),
                TownsteadVillagers.size(),
                TownsteadVillagers.dirtyCount(),
                VillageAiBudget.scopeCount()
        );
    }
}
