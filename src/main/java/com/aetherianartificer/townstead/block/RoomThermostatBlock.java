package com.aetherianartificer.townstead.block;

import com.aetherianartificer.townstead.temperature.RoomHeat;
import com.aetherianartificer.townstead.temperature.ThermalDemand;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

/** A player-wired controller. Settings and hysteresis state persist in block state. */
public final class RoomThermostatBlock extends Block {
    public enum Mode implements StringRepresentable {
        OFF, HEAT, COOL;
        public String getSerializedName() { return name().toLowerCase(java.util.Locale.ROOT); }
    }
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final IntegerProperty TARGET = IntegerProperty.create("target", 5, 35);
    public static final EnumProperty<Mode> MODE = EnumProperty.create("mode",Mode.class);
    public RoomThermostatBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING,Direction.NORTH).setValue(POWERED,false)
                .setValue(TARGET,20).setValue(MODE,Mode.HEAT));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) {
        builder.add(FACING,POWERED,TARGET,MODE);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getClickedFace();
        if (!facing.getAxis().isHorizontal()) return null;
        BlockState state = defaultBlockState().setValue(FACING,facing);
        return state.canSurvive(context.getLevel(),context.getClickedPos()) ? state : null;
    }
    @Override public boolean canSurvive(BlockState state,LevelReader level,BlockPos pos) {
        Direction support = state.getValue(FACING).getOpposite();
        return Block.canSupportCenter(level,pos.relative(support),support.getOpposite());
    }
    @Override public BlockState updateShape(BlockState state,Direction direction,BlockState neighbor,LevelAccessor level,BlockPos pos,BlockPos other) {
        return direction == state.getValue(FACING).getOpposite() && !state.canSurvive(level,pos)
                ? Blocks.AIR.defaultBlockState() : super.updateShape(state,direction,neighbor,level,pos,other);
    }
    @Override public VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> Block.box(4.975,4.425,0,11.025,11.575,2.255);
            case WEST -> Block.box(13.745,4.425,4.975,16,11.575,11.025);
            case EAST -> Block.box(0,4.425,4.975,2.255,11.575,11.025);
            default -> Block.box(4.975,4.425,13.745,11.025,11.575,16);
        };
    }
    @Override public void onPlace(BlockState state,Level level,BlockPos pos,BlockState old,boolean moved) {
        super.onPlace(state,level,pos,old,moved);
        if (!level.isClientSide) level.scheduleTick(pos,this,1);
    }
    @Override public void tick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random) {
        updateDemand(state,level,pos);
        level.scheduleTick(pos,this,20);
    }
    public void updateDemand(BlockState state,ServerLevel level,BlockPos pos) {
        var air = RoomHeat.controlAirAt(level,pos.relative(state.getValue(FACING)));
        Mode mode = state.getValue(MODE);
        boolean powered = mode != Mode.OFF && air.isPresent()
                && ThermalDemand.next(mode == Mode.HEAT,state.getValue(POWERED),air.getAsDouble(),state.getValue(TARGET),1);
        if (powered != state.getValue(POWERED)) level.setBlock(pos,state.setValue(POWERED,powered),Block.UPDATE_ALL);
    }
    @Override public boolean isSignalSource(BlockState state) { return true; }
    @Override public int getSignal(BlockState state,BlockGetter level,BlockPos pos,Direction direction) {
        return state.getValue(POWERED) ? 15 : 0;
    }
    @Override public void onRemove(BlockState state,Level level,BlockPos pos,BlockState replacement,boolean moved) {
        super.onRemove(state,level,pos,replacement,moved);
        if (!state.is(replacement.getBlock()) && state.getValue(POWERED)) level.updateNeighborsAt(pos,this);
    }
    @Override
    //? if >=1.21 {
    protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit) {
    //?} else {
    /*public InteractionResult use(BlockState state,Level level,BlockPos pos,Player player,net.minecraft.world.InteractionHand hand,BlockHitResult hit) {
    *///?}
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)
            com.aetherianartificer.townstead.temperature.ThermostatInteraction.send(serverPlayer,pos,true);
        return InteractionResult.CONSUME;
    }
    @Override public BlockState rotate(BlockState state,Rotation rotation) { return state.setValue(FACING,rotation.rotate(state.getValue(FACING))); }
    @Override public BlockState mirror(BlockState state,Mirror mirror) { return state.rotate(mirror.getRotation(state.getValue(FACING))); }
}
