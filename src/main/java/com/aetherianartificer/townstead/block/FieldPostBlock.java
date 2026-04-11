package com.aetherianartificer.townstead.block;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.farming.FieldPostConfigSyncPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class FieldPostBlock extends Block implements EntityBlock, SimpleWaterloggedBlock {
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    // 4x16x4 centered column (like a fence post)
    private static final VoxelShape SHAPE = Block.box(6, 0, 6, 10, 16, 10);

    public FieldPostBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(WATERLOGGED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(WATERLOGGED);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        FluidState fluid = context.getLevel().getFluidState(context.getClickedPos());
        return defaultBlockState().setValue(WATERLOGGED, fluid.getType() == Fluids.WATER);
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    //? if >=1.21 {
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                Player player, BlockHitResult hitResult) {
    //?} else {
    /*public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                 Player player, net.minecraft.world.InteractionHand hand, BlockHitResult hitResult) {
    *///?}
        if (level.isClientSide()) {
            // Client side: open the screen directly (no menu = no JEI)
            townstead$openScreenClient(pos);
            return InteractionResult.SUCCESS;
        }
        // Server side: send config sync to player
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof FieldPostBlockEntity fieldPost) {
            FieldPostConfigSyncPayload payload = new FieldPostConfigSyncPayload(
                    pos, fieldPost.toConfig(),
                    fieldPost.getEffectivePatternId(), 0, 0, 0, 0
            );
            //? if neoforge {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer((ServerPlayer) player, payload);
            //?} else if forge {
            /*com.aetherianartificer.townstead.TownsteadNetwork.sendToPlayer((ServerPlayer) player, payload);
            *///?}
        }
        return InteractionResult.CONSUME;
    }

    private static void townstead$openScreenClient(BlockPos pos) {
        try {
            Class<?> screenOpener = Class.forName("com.aetherianartificer.townstead.client.gui.fieldpost.FieldPostScreenOpener");
            screenOpener.getMethod("open", BlockPos.class).invoke(null, pos);
        } catch (Exception ignored) {
            // Dedicated server safety: client class not available
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FieldPostBlockEntity(pos, state);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            FieldPostIndex.remove(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
