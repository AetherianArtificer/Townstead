package com.aetherianartificer.townstead.api.v1.model;

import net.minecraft.core.BlockPos;

/**
 * One MCA building as Townstead sees it. {@link #type} is the effective type id, {@link #family}
 * the type without its tier suffix, {@link #tier} the number from a {@code _lN} suffix or 1.
 */
public record BuildingSnapshot(
        VillageId village,
        int id,
        String type,
        String family,
        int tier,
        int size,
        BlockPos center,
        BlockPos min,
        BlockPos max,
        boolean complete
) {
}
