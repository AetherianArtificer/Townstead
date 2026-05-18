package com.aetherianartificer.townstead.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Townstead calendar — a thin panel that mounts on walls or sits flat on top
 * of a block. Right-click opens the calendar GUI.
 *
 * Placement model mirrors {@code FaceAttachedHorizontalDirectionalBlock}:
 * {@link AttachFace#WALL} for vertical mounts (hung on a wall, faces outward
 * in the FACING direction), {@link AttachFace#FLOOR} for flat-on-top mounts
 * (lies on the upper face of the block below). Ceiling is intentionally
 * skipped — a calendar dangling face-down is awkward and the model wouldn't
 * read.
 */
public class CalendarBlock extends Block implements SimpleWaterloggedBlock {
    public static final EnumProperty<AttachFace> ATTACH_FACE = BlockStateProperties.ATTACH_FACE;
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    // Thin 1px panel against the surface — wall variant projects out, floor variant
    // lies flat on top. The vanilla blockstate model rotates the same panel shape.
    private static final VoxelShape SHAPE_FLOOR = Block.box(0.5, 0.0, 0.5, 15.5, 1.0, 15.5);
    private static final VoxelShape SHAPE_WALL_NORTH = Block.box(0.5, 0.5, 15.0, 15.5, 15.5, 16.0);
    private static final VoxelShape SHAPE_WALL_SOUTH = Block.box(0.5, 0.5, 0.0, 15.5, 15.5, 1.0);
    private static final VoxelShape SHAPE_WALL_WEST = Block.box(15.0, 0.5, 0.5, 16.0, 15.5, 15.5);
    private static final VoxelShape SHAPE_WALL_EAST = Block.box(0.0, 0.5, 0.5, 1.0, 15.5, 15.5);

    public CalendarBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(ATTACH_FACE, AttachFace.WALL)
                .setValue(FACING, Direction.NORTH)
                .setValue(WATERLOGGED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ATTACH_FACE, FACING, WATERLOGGED);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        AttachFace face = state.getValue(ATTACH_FACE);
        if (face == AttachFace.FLOOR) return SHAPE_FLOOR;
        // WALL — pick by horizontal FACING
        return switch (state.getValue(FACING)) {
            case NORTH -> SHAPE_WALL_NORTH;
            case SOUTH -> SHAPE_WALL_SOUTH;
            case WEST -> SHAPE_WALL_WEST;
            case EAST -> SHAPE_WALL_EAST;
            default -> Shapes.empty();
        };
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        // Visual only; don't block movement.
        return Shapes.empty();
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction clicked = ctx.getClickedFace();
        AttachFace face = clicked == Direction.UP
                ? AttachFace.FLOOR
                : (clicked == Direction.DOWN ? null : AttachFace.WALL);
        if (face == null) return null; // ceiling not supported

        Direction facing = (face == AttachFace.FLOOR)
                ? ctx.getHorizontalDirection().getOpposite()
                : clicked; // wall: face out from the wall

        FluidState fluid = ctx.getLevel().getFluidState(ctx.getClickedPos());
        return defaultBlockState()
                .setValue(ATTACH_FACE, face)
                .setValue(FACING, facing)
                .setValue(WATERLOGGED, fluid.getType() == Fluids.WATER);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        AttachFace face = state.getValue(ATTACH_FACE);
        Direction supportSide = (face == AttachFace.FLOOR)
                ? Direction.DOWN
                : state.getValue(FACING).getOpposite();
        BlockPos supportPos = pos.relative(supportSide);
        return !level.getBlockState(supportPos).isAir();
    }

    @Override
    public BlockState updateShape(BlockState state, Direction side, BlockState neighbor,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        if (!canSurvive(state, level, pos)) return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        return state;
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    //? if >=1.21 {
    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                            Player player, BlockHitResult hit) {
        if (level.isClientSide()) {
            com.aetherianartificer.townstead.client.gui.calendar.CalendarScreenOpener.open();
        }
        return InteractionResult.SUCCESS;
    }
    //?} else {
    /*@Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                  net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide()) {
            com.aetherianartificer.townstead.client.gui.calendar.CalendarScreenOpener.open();
        }
        return InteractionResult.SUCCESS;
    }
    *///?}
}
