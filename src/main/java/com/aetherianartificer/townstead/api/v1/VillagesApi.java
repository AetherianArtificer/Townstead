package com.aetherianartificer.townstead.api.v1;

import com.aetherianartificer.townstead.api.v1.model.BuildingSnapshot;
import com.aetherianartificer.townstead.api.v1.model.SpiritSnapshot;
import com.aetherianartificer.townstead.api.v1.model.VillageId;
import com.aetherianartificer.townstead.api.v1.model.VillageNeedsSummary;
import com.aetherianartificer.townstead.api.v1.model.VillageSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.Optional;

/** Village-scoped reads: identity, residents, buildings, spirit, and aggregate needs. */
public interface VillagesApi {

    Optional<VillageSnapshot> village(MinecraftServer server, VillageId id);

    Optional<VillageSnapshot> village(ServerLevel level, int villageId);

    /** The village whose border contains {@code pos}, or the nearest within MCA's merge margin. */
    Optional<VillageSnapshot> nearest(ServerLevel level, BlockPos pos);

    List<VillageSnapshot> all(ServerLevel level);

    /** The villager's home village, falling back to the nearest one. Empty for non-villagers. */
    Optional<VillageId> homeOf(Entity entity);

    Optional<SpiritSnapshot> spirit(MinecraftServer server, VillageId id);

    List<BuildingSnapshot> buildings(MinecraftServer server, VillageId id);

    Optional<BuildingSnapshot> buildingAt(ServerLevel level, BlockPos pos);

    /**
     * Aggregate needs over every resident Townstead has a record for, loaded or not. The counts
     * in the summary say how much of the village the numbers cover.
     */
    Optional<VillageNeedsSummary> needs(MinecraftServer server, VillageId id);

    /** Registered spirit ids, in Townstead's canonical order. */
    List<String> spiritIds();

    boolean isKnownSpirit(String spiritId);

    /** The spirit tier thresholds, ascending. */
    List<Integer> spiritTierThresholds();
}
