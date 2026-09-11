package com.aetherianartificer.townstead.block;

import com.aetherianartificer.townstead.snow.SnowCoating;
import com.aetherianartificer.townstead.snow.SnowCoatingPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.levelgen.Heightmap;

/** Persistent, automatically synchronized cosmetic snow; no extra block entities or packets. */
public class SnowCoatedBlock extends Block {
    public static final BooleanProperty SNOW_COATED = BooleanProperty.create("snow_coated");

    public SnowCoatedBlock(Properties properties) {
        super(properties.randomTicks());
        registerDefaultState(defaultBlockState().setValue(SNOW_COATED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SNOW_COATED);
    }

    @Override
    public boolean isRandomlyTicking(BlockState state) {
        // Also clean up saved coatings if the seasonal mod was removed or Ecliptic was added.
        return state.getValue(SNOW_COATED) || SnowCoating.active();
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockPos above = pos.above();
        boolean exposed = level.canSeeSky(above)
                && level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ()) <= above.getY()
                && level.getBlockState(above).isAir();
        boolean coated = state.getValue(SNOW_COATED);
        boolean next = SnowCoatingPolicy.next(coated, SnowCoating.weather(level, pos), exposed,
                !state.getFluidState().isEmpty(), level.getBrightness(LightLayer.BLOCK, pos));
        if (next != coated) level.setBlock(pos, state.setValue(SNOW_COATED, next), Block.UPDATE_CLIENTS);
    }
}
