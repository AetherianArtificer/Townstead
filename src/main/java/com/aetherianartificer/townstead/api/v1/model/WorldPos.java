package com.aetherianartificer.townstead.api.v1.model;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/** A block in a dimension. Added in revision 2. */
public record WorldPos(
        ResourceLocation dimension,
        BlockPos pos
) {
}
