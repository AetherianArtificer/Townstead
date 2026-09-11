package com.aetherianartificer.townstead.api.v1.model;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;
import java.util.UUID;

/**
 * A registered worksite. {@code binding} names how it was recognised (an anchor block, a
 * building, a station); {@code driver} is the villager currently running it, when any.
 */
public record WorksiteSnapshot(
        long id,
        ResourceLocation binding,
        ResourceLocation dimension,
        BlockPos anchor,
        String name,
        boolean nameCustom,
        Optional<VillageId> village,
        Optional<UUID> driver,
        long createdGameTime,
        long lastSeenGameTime,
        int orderCount
) {
    public WorksiteSnapshot {
        village = village == null ? Optional.empty() : village;
        driver = driver == null ? Optional.empty() : driver;
    }
}
