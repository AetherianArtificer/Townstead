package com.aetherianartificer.townstead.work.station;

import com.aetherianartificer.townstead.work.WorkHazards;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Datapack-extensible pathing rules for hot and unstandable workstation blocks. */
public final class WorkstationHazards implements WorkHazards.WorkHazard {

    private static final TagKey<Block> HOT_WHEN_LIT = tag("hot_when_lit");
    private static final TagKey<Block> HEATED_WORK_SURFACES = tag("heated_work_surfaces");
    private static final TagKey<Block> UNSAFE_WORK_SURFACES = tag("unsafe_work_surfaces");

    private WorkstationHazards() {}

    public static void bootstrap() {
        WorkHazards.register(new WorkstationHazards());
    }

    @Override
    public boolean hazardous(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.is(HOT_WHEN_LIT) && isLit(state)) return true;
        return state.is(HEATED_WORK_SURFACES) && isLit(level.getBlockState(pos.below()));
    }

    @Override
    public boolean unsafeSurface(BlockGetter level, BlockPos pos) {
        return level.getBlockState(pos).is(UNSAFE_WORK_SURFACES);
    }

    private static boolean isLit(BlockState state) {
        return state.is(HOT_WHEN_LIT)
                && state.hasProperty(BlockStateProperties.LIT)
                && state.getValue(BlockStateProperties.LIT);
    }

    private static TagKey<Block> tag(String path) {
        //? if >=1.21 {
        return TagKey.create(Registries.BLOCK, ResourceLocation.parse("townstead:" + path));
        //?} else {
        /*return TagKey.create(Registries.BLOCK, new ResourceLocation("townstead", path));
        *///?}
    }
}
