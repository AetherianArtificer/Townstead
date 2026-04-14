package com.aetherianartificer.townstead.compat.farming;

import com.aetherianartificer.townstead.compat.ModCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Set;

/**
 * Creepy Delight compat. The mod is MCreator-generated and its crop plants extend FlowerBlock —
 * which we normally filter out — but they explicitly override {@code mayPlaceOn} to accept vanilla
 * FARMLAND. So we opt them in via this provider's {@code isSeed} whitelist; the generic place/break
 * harvest path handles everything else since each growth stage is a CropBlock-style sibling.
 *
 * <p>The mod's neoforge modid is {@code creepy_delight} but its items and blocks are registered
 * under the {@code creepy_crops_delight} namespace, so we key off that for recognition and use
 * the modid for loaded-state detection.</p>
 */
public final class CreepyDelightCropCompat implements FarmerCropCompat {
    private static final String MOD_ID = "creepy_delight";
    private static final String NAMESPACE = "creepy_crops_delight";

    private static final Set<String> SEED_PATHS = Set.of(
            "eyegg_seeds",
            "scourgeweed_seeds",
            "toothflower_seedss" // sic — typo in the mod
    );

    @Override
    public String modId() { return MOD_ID; }

    @Override
    public boolean isSeed(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ResourceLocation key = stack.getItem().builtInRegistryHolder().key().location();
        return NAMESPACE.equals(key.getNamespace()) && SEED_PATHS.contains(key.getPath());
    }

    @Override
    public boolean shouldPartialHarvest(BlockState state) { return false; }

    @Override
    public List<ItemStack> doPartialHarvest(ServerLevel level, BlockPos pos, BlockState state) { return List.of(); }

    @Override
    public boolean isExistingFarmSoil(ServerLevel level, BlockPos pos) { return false; }

    @Override
    public boolean isPlantableSpot(ServerLevel level, BlockPos pos) { return false; }
}
