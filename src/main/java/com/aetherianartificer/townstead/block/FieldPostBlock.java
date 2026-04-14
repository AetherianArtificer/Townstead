package com.aetherianartificer.townstead.block;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.farming.CropProductResolver;
import com.aetherianartificer.townstead.farming.FieldPostConfigSyncPayload;
import com.aetherianartificer.townstead.farming.FieldPostGridSyncPayload;
import com.aetherianartificer.townstead.farming.GridScanner;
import com.aetherianartificer.townstead.farming.GridSnapshot;
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
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class FieldPostBlock extends Block implements EntityBlock, SimpleWaterloggedBlock {
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    // 4x16x4 centered column (like a fence post)
    private static final VoxelShape SHAPE = Block.box(6, 0, 6, 10, 16, 10);

    public FieldPostBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(WATERLOGGED, false)
                .setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(WATERLOGGED, FACING);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        FluidState fluid = context.getLevel().getFluidState(context.getClickedPos());
        return defaultBlockState()
                .setValue(WATERLOGGED, fluid.getType() == Fluids.WATER)
                .setValue(FACING, context.getHorizontalDirection().getOpposite());
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
        // Server side: build snapshot and send config + grid data to player
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof FieldPostBlockEntity fieldPost && level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            // Config sync
            int[] status = GridScanner.computeStatus(
                    GridScanner.scan(serverLevel, pos, fieldPost.getRadius()));
            FieldPostConfigSyncPayload configPayload = new FieldPostConfigSyncPayload(
                    pos, fieldPost.toConfig(),
                    fieldPost.getEffectivePatternId(),
                    status[0], status[1], status[2], status[3]
            );
            // Grid snapshot
            GridSnapshot snapshot = GridScanner.scan(serverLevel, pos, fieldPost.getRadius());
            CropProductResolver resolver = CropProductResolver.get(serverLevel);
            java.util.Map<String, Integer> seedCounts = GridScanner.countVillageSeeds(
                    serverLevel, pos, fieldPost.getRadius());
            // Encode soil compatibility as a bitmask per seed (bit N = SoilType.values()[N])
            java.util.Map<String, Integer> compatBits = new java.util.HashMap<>();
            com.aetherianartificer.townstead.farming.cellplan.SoilType[] allSoil =
                    com.aetherianartificer.townstead.farming.cellplan.SoilType.values();
            resolver.getSoilCompatMap().forEach((seedId, soils) -> {
                int bits = 0;
                for (com.aetherianartificer.townstead.farming.cellplan.SoilType s : soils) bits |= (1 << s.ordinal());
                compatBits.put(seedId, bits);
            });
            FieldPostGridSyncPayload gridPayload = new FieldPostGridSyncPayload(
                    pos, snapshot, resolver.getPalette(), seedCounts, compatBits,
                    status[0], status[1], status[2], status[3]
            );
            //? if neoforge {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer((ServerPlayer) player, configPayload);
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer((ServerPlayer) player, gridPayload);
            //?} else if forge {
            /*com.aetherianartificer.townstead.TownsteadNetwork.sendToPlayer((ServerPlayer) player, configPayload);
            com.aetherianartificer.townstead.TownsteadNetwork.sendToPlayer((ServerPlayer) player, gridPayload);
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
