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
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The Order Sheet: a worksite's production orders, readable at any hour.
 *
 * <p>The around-the-clock door. Asking a worker is free but only while they are on shift; a sheet
 * costs paper and answers at midnight. Both open the same list, because the list belongs to the
 * place rather than to whoever let you at it.</p>
 *
 * <p>The sheet does not define the worksite, it only points at one: it opens whatever place covers
 * the block it rests on. Pin it to a kitchen wall and it opens that kitchen, lay it on a counter
 * beside a smoker in a yard and it opens the smoker's post. Inside the village Archives the same
 * sheet is the career desk instead: it opens the career screen, and it is the block the Archives
 * is detected by.</p>
 */
public class OrderSheetBlock extends Block {

    public static final EnumProperty<AttachFace> FACE = BlockStateProperties.ATTACH_FACE;
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    private static final VoxelShape FLOOR = Block.box(1, 0, 1, 15, 1, 15);
    private static final VoxelShape CEILING = Block.box(1, 15, 1, 15, 16, 15);
    private static final VoxelShape WALL_NORTH = Block.box(1, 1, 15, 15, 15, 16);
    private static final VoxelShape WALL_SOUTH = Block.box(1, 1, 0, 15, 15, 1);
    private static final VoxelShape WALL_WEST = Block.box(15, 1, 1, 16, 15, 15);
    private static final VoxelShape WALL_EAST = Block.box(0, 1, 1, 1, 15, 15);

    public OrderSheetBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACE, AttachFace.FLOOR)
                .setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACE, FACING);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        BlockState state;
        if (face.getAxis().isHorizontal()) {
            state = defaultBlockState().setValue(FACE, AttachFace.WALL).setValue(FACING, face);
        } else {
            state = defaultBlockState()
                    .setValue(FACE, face == Direction.UP ? AttachFace.FLOOR : AttachFace.CEILING)
                    .setValue(FACING, context.getHorizontalDirection().getOpposite());
        }
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }

    private static Direction supportDirection(BlockState state) {
        return switch (state.getValue(FACE)) {
            case FLOOR -> Direction.DOWN;
            case CEILING -> Direction.UP;
            case WALL -> state.getValue(FACING).getOpposite();
        };
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction toSupport = supportDirection(state);
        BlockPos support = pos.relative(toSupport);
        return Block.canSupportCenter(level, support, toSupport.getOpposite());
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction == supportDirection(state) && !state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (state.getValue(FACE) == AttachFace.FLOOR) {
            return FLOOR;
        }
        if (state.getValue(FACE) == AttachFace.CEILING) {
            return CEILING;
        }
        return switch (state.getValue(FACING)) {
            case SOUTH -> WALL_SOUTH;
            case WEST -> WALL_WEST;
            case EAST -> WALL_EAST;
            default -> WALL_NORTH;
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
            // In the Archives the sheet is the career desk; everywhere else it is the order list.
            if (com.aetherianartificer.townstead.village.ArchivesBuilding
                    .villageIfInside(serverPlayer, pos).isPresent()) {
                com.aetherianartificer.townstead.profession.career.CareerTreeOpener.send(serverPlayer);
                return InteractionResult.CONSUME;
            }
            // Posting a sheet is a deliberate act, so it may mint the worksite it names rather
            // than waiting for a villager to work there first.
            Worksite site = OrdersOpener.siteAt(serverLevel, pos, true);
            if (site == null || !OrdersOpener.open(serverPlayer, site)) {
                serverPlayer.displayClientMessage(
                        Component.translatable("townstead.orders.sheet.no_worksite"), true);
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
