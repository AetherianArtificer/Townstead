package com.aetherianartificer.townstead.compat.farming;

import com.aetherianartificer.townstead.compat.ModCompat;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public final class FarmerRemovableWeedCompatRegistry {
    private static final List<FarmerRemovableWeedCompat> PROVIDERS = List.of(
            new FarmersDelightRemovableWeedCompat()
    );

    private FarmerRemovableWeedCompatRegistry() {}

    public static boolean isRemovableWeed(BlockState state) {
        for (FarmerRemovableWeedCompat provider : PROVIDERS) {
            if (!ModCompat.isLoaded(provider.modId())) continue;
            if (provider.isRemovableWeed(state)) return true;
        }
        return false;
    }
}
