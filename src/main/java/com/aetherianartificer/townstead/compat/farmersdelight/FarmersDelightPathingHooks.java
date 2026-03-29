package com.aetherianartificer.townstead.compat.farmersdelight;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public final class FarmersDelightPathingHooks {
    private FarmersDelightPathingHooks() {}

    public static boolean isHazardousCookware(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (isLitStove(state)) return true;
        if (isFarmersDelightBlock(state, "cooking_pot") || isFarmersDelightBlock(state, "skillet")) {
            return isLitStoveBelow(level, pos);
        }
        return false;
    }

    public static boolean isUnsafeWorkSurface(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return isFarmersDelightBlock(state, "stove")
                || isFarmersDelightBlock(state, "skillet")
                || isFarmersDelightBlock(state, "cooking_pot");
    }

    private static boolean isLitStoveBelow(BlockGetter level, BlockPos pos) {
        return isLitStove(level.getBlockState(pos.below()));
    }

    private static boolean isLitStove(BlockState state) {
        return isFarmersDelightBlock(state, "stove")
                && state.hasProperty(BlockStateProperties.LIT)
                && state.getValue(BlockStateProperties.LIT);
    }

    private static boolean isFarmersDelightBlock(BlockState state, String path) {
        var id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return id != null && "farmersdelight".equals(id.getNamespace()) && path.equals(id.getPath());
    }
}
