package com.aetherianartificer.townstead.compat.farming;

import com.aetherianartificer.townstead.compat.ModCompat;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class FarmerStockDroppableCompatRegistry {
    private static final List<FarmerStockDroppableCompat> PROVIDERS = List.of(
            new FarmersDelightStockDroppableCompat()
    );

    private FarmerStockDroppableCompatRegistry() {}

    public static boolean isForcedStockDroppable(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        for (FarmerStockDroppableCompat provider : PROVIDERS) {
            if (!ModCompat.isLoaded(provider.modId())) continue;
            if (provider.isForcedStockDroppable(stack)) return true;
        }
        return false;
    }
}
