package com.aetherianartificer.townstead.compat.farming;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public interface FarmerHarvestToolCompat {
    String modId();

    boolean matches(ItemStack stack);

    default boolean shouldUseForBlock(BlockState state) {
        return true;
    }
}
