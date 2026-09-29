package com.aetherianartificer.townstead.compat.farming;

import com.aetherianartificer.townstead.compat.ModCompat;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * TerraFirmaCraft crops die into {@code tfc:dead_crop/*} blocks (a BushBlock with only a
 * "mature" flag, no age), which no harvest path recognizes — without this they occupy their
 * cell forever. Treating them as removable weeds lets groom clear them; TFC's dead-crop loot
 * returns 1-3 seeds, so the cell replants from the recovered drops. Wild crops ({@code tfc:wild_crop/*})
 * have no growth stage either and would block a planned cell the same way; clearing one drops its seeds.
 */
public final class TfcRemovableWeedCompat implements FarmerRemovableWeedCompat {
    @Override
    public String modId() {
        return "tfc";
    }

    @Override
    public boolean isRemovableWeed(BlockState state) {
        ResourceLocation key = state.getBlock().builtInRegistryHolder().key().location();
        return ModCompat.isFromLoadedMod(key, modId())
                && (key.getPath().startsWith("dead_crop/") || key.getPath().startsWith("wild_crop/"));
    }
}
