package com.aetherianartificer.townstead.compat.farming;

import com.aetherianartificer.townstead.compat.ModCompat;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

public final class FarmersDelightRemovableWeedCompat implements FarmerRemovableWeedCompat {
    @Override
    public String modId() {
        return "farmersdelight";
    }

    @Override
    public boolean isRemovableWeed(BlockState state) {
        ResourceLocation key = state.getBlock().builtInRegistryHolder().key().location();
        return ModCompat.matchesLoadedModPath(key, modId(), "sandy_shrub");
    }
}
