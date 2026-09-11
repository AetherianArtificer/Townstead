package com.aetherianartificer.townstead.api.v1.model;

import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.UUID;

/**
 * A village's identity and roll. {@link #residentCount} is MCA's roll and is unload-safe;
 * {@link #loadedResidentCount} is how many of them are currently in the world.
 */
public record VillageSnapshot(
        VillageId id,
        String name,
        BlockPos center,
        BlockPos min,
        BlockPos max,
        int residentCount,
        int loadedResidentCount,
        List<UUID> residents,
        long establishedWorldDay,
        boolean playerFounded,
        int buildingCount
) {
    public VillageSnapshot {
        residents = residents == null ? List.of() : List.copyOf(residents);
    }
}
