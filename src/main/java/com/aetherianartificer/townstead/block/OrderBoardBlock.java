package com.aetherianartificer.townstead.block;

import com.aetherianartificer.townstead.work.order.OrdersOpener;
import com.aetherianartificer.townstead.work.site.Worksite;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The Order Board: a worksite's production orders, readable at any hour.
 *
 * <p>The around-the-clock door. Asking a worker is free but only while they are on shift; a board
 * costs planks and answers at midnight. Both open the same list, because the list belongs to the
 * place rather than to whoever let you at it.</p>
 *
 * <p>The board does not define the worksite, it only points at one: it opens whatever place covers
 * the block it hangs on. Hang it in a kitchen and it opens that kitchen, hang it beside a smoker in
 * a yard and it opens the smoker's post.</p>
 */
public class OrderBoardBlock extends Block {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    private static final VoxelShape NORTH = Block.box(0, 1, 14, 16, 15, 16);
    private static final VoxelShape SOUTH = Block.box(0, 1, 0, 16, 15, 2);
    private static final VoxelShape WEST = Block.box(14, 1, 0, 16, 15, 16);
    private static final VoxelShape EAST = Block.box(0, 1, 0, 2, 15, 16);

    public OrderBoardBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        Direction facing = face.getAxis().isHorizontal()
                ? face : context.getHorizontalDirection().getOpposite();
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            case EAST -> EAST;
            default -> NORTH;
        };
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
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer && level instanceof ServerLevel serverLevel) {
            // Hanging a board is a deliberate act, so it may mint the worksite it names rather
            // than waiting for a villager to work there first.
            Worksite site = OrdersOpener.siteAt(serverLevel, pos, true);
            if (site == null || !OrdersOpener.open(serverPlayer, site)) {
                serverPlayer.displayClientMessage(
                        Component.translatable("townstead.orders.board.no_worksite"), true);
            }
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
