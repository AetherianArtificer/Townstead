package com.aetherianartificer.townstead.compat.farming;

import net.minecraft.world.item.ItemStack;

public interface FarmerStockDroppableCompat {
    String modId();

    boolean isForcedStockDroppable(ItemStack stack);
}
