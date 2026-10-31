package com.aetherianartificer.townstead.api.v1.model;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/** A villager's visit to a hangout venue. {@code phase} is {@code traveling}, {@code present}, {@code complete} or {@code interrupted}. */
public record HangoutVisitSnapshot(
        UUID visitId,
        UUID visitor,
        ResourceLocation dimension,
        ResourceLocation venue,
        int buildingId,
        ResourceLocation policy,
        BlockPos venueAnchor,
        BlockPos spot,
        ResourceLocation posture,
        String phase,
        long createdAt,
        long presentAt,
        long deadline,
        String exitReason
) {
    public HangoutVisitSnapshot {
        exitReason = exitReason == null ? "" : exitReason;
    }
}
