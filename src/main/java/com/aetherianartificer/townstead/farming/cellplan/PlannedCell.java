package com.aetherianartificer.townstead.farming.cellplan;

import net.minecraft.core.BlockPos;

/**
 * One painted cell from a Field Post's plan, resolved to world coordinates.
 * Protected cells are never materialized as PlannedCells — they're simply absent from the plan.
 *
 * @param soilPos         the soil block position (one below the crop)
 * @param cropPos         soilPos.above()
 * @param desiredSoil     FARMLAND or RICH_SOIL (NONE/PROTECTED cells aren't emitted)
 * @param seedAssignment  seed item registry id, {@link SeedAssignment#AUTO}, or {@link SeedAssignment#NONE}
 */
public record PlannedCell(
        BlockPos soilPos,
        BlockPos cropPos,
        SoilType desiredSoil,
        String seedAssignment
) {
    public PlannedCell(BlockPos soilPos, SoilType desiredSoil, String seedAssignment) {
        this(soilPos.immutable(), soilPos.above().immutable(), desiredSoil, seedAssignment);
    }
}
