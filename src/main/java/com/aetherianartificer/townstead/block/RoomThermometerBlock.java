package com.aetherianartificer.townstead.block;

import com.aetherianartificer.townstead.temperature.TemperatureData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/** A wall-mounted thermometer whose liquid level tracks the climate system. */
public final class RoomThermometerBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<ThermometerBand> TEMPERATURE =
            EnumProperty.create("temperature", ThermometerBand.class);
    private static final int REFRESH_TICKS = 100;

    private static final VoxelShape NORTH = Block.box(5, 0.5, 13.75, 11, 15.5, 16);
    private static final VoxelShape SOUTH = Block.box(5, 0.5, 0, 11, 15.5, 2.25);
    private static final VoxelShape WEST = Block.box(13.75, 0.5, 5, 16, 15.5, 11);
    private static final VoxelShape EAST = Block.box(0, 0.5, 5, 2.25, 15.5, 11);

    public RoomThermometerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(TEMPERATURE, ThermometerBand.MILD));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, TEMPERATURE);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        if (!face.getAxis().isHorizontal()) return null;
        BlockState state = defaultBlockState().setValue(FACING, face);
        if (context.getLevel() instanceof ServerLevel serverLevel) {
            state = state.setValue(TEMPERATURE,
                    ThermometerBand.at(TemperatureData.ambientCelsius(serverLevel, context.getClickedPos())));
        }
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }

    private static Direction supportDirection(BlockState state) {
        return state.getValue(FACING).getOpposite();
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide && !oldState.is(this)) level.scheduleTick(pos, this, 1);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        updateDisplay(state, level, pos, TemperatureData.ambientCelsius(level, pos));
        level.scheduleTick(pos, this, REFRESH_TICKS);
    }

    private void updateDisplay(BlockState state, ServerLevel level, BlockPos pos, float celsius) {
        ThermometerBand band = state.getValue(TEMPERATURE).update(celsius);
        if (band != state.getValue(TEMPERATURE)) {
            level.setBlock(pos, state.setValue(TEMPERATURE, band), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction supportDirection = supportDirection(state);
        BlockPos support = pos.relative(supportDirection);
        return Block.canSupportCenter(level, support, supportDirection.getOpposite());
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
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                               CollisionContext context) {
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
                                 Player player, net.minecraft.world.InteractionHand hand,
                                 BlockHitResult hitResult) {
    *///?}
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (level instanceof ServerLevel serverLevel) {
            float celsius = TemperatureData.ambientCelsius(serverLevel, pos);
            updateDisplay(state, serverLevel, pos, celsius);
            var reading = new com.aetherianartificer.townstead.temperature.ThermometerReadingPayload(celsius,
                    com.aetherianartificer.townstead.compat.temperature.TemperatureBridgeResolver.get()
                            == com.aetherianartificer.townstead.compat.temperature.ColdSweatTemperatureBridge.INSTANCE);
            //? if neoforge {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer((net.minecraft.server.level.ServerPlayer) player, reading);
            //?} else {
            /*com.aetherianartificer.townstead.TownsteadNetwork.sendToPlayer((net.minecraft.server.level.ServerPlayer) player, reading);
            *///?}
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
