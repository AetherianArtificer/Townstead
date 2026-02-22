package com.aetherianartificer.townstead.compat.farming;

import com.aetherianartificer.townstead.compat.ModCompat;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public final class FarmersDelightStockDroppableCompat implements FarmerStockDroppableCompat {
    @Override
    public String modId() {
        return "farmersdelight";
    }

    @Override
    public boolean isForcedStockDroppable(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ResourceLocation key = stack.getItem().builtInRegistryHolder().key().location();
        return ModCompat.matchesLoadedModPath(key, modId(), "straw");
    }
}
