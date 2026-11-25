package com.aetherianartificer.townstead.api.v1.model;

import net.minecraft.core.BlockPos;

/**
 * One piece of a village as the world generated it, such as a house, a smithy or a farm, before or apart from what MCA
 * registers as buildings. The box is inclusive at both corners. Added in revision 2.
 */
public record GeneratedPieceSnapshot(
        BlockPos min,
        BlockPos max
) {
}
