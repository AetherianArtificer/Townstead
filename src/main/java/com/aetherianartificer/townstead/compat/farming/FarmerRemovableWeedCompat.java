package com.aetherianartificer.townstead.compat.farming;

import net.minecraft.world.level.block.state.BlockState;

public interface FarmerRemovableWeedCompat {
    String modId();

    boolean isRemovableWeed(BlockState state);
}
