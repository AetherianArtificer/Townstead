package com.aetherianartificer.townstead.api.v1.model;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A village's identity and roll. {@link #residentCount} is MCA's roll and is unload-safe;
 * {@link #loadedResidentCount} is how many of them are currently in the world. {@link #min} and
 * {@link #max} bound the buildings; the town range is that box grown by {@link #rangeMargin} on
 * every side. The margin grows as the town gets stronger.
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
        int buildingCount,
        Optional<ResourceLocation> culture,
        int rangeMargin
) {
    public VillageSnapshot {
        residents = residents == null ? List.of() : List.copyOf(residents);
        culture = culture == null ? Optional.empty() : culture;
    }
}
