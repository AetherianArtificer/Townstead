package com.aetherianartificer.townstead.block;

import com.aetherianartificer.townstead.food.ServingPlateService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
//? if >=1.21 {
import net.minecraft.world.ItemInteractionResult;
//?}
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/** A small, UI-free place for one finished dish. */
public final class ServingPlateBlock extends Block implements EntityBlock {
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 1.5, 14);

    public ServingPlateBlock(Properties properties) { super(properties); }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos support = pos.below();
        return level.getBlockState(support).isFaceSturdy(level, support, Direction.UP);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return direction == Direction.DOWN && !canSurvive(state, level, pos)
                ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ServingPlateBlockEntity(pos, state);
    }

    //? if >=1.21 {
    @Override
    protected ItemInteractionResult useItemOn(ItemStack held, BlockState state, Level level,
                                              BlockPos pos, Player player,
                                              net.minecraft.world.InteractionHand hand,
                                              BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof ServingPlateBlockEntity plate) || !plate.isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!ServingPlateService.canServe(held)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!level.isClientSide() && plate.place(held)) {
            if (!player.getAbilities().instabuild) held.shrink(1);
        }
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        return interactEmptyHand(level, pos, player);
    }
    //?} else {
    /*@Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        ItemStack held = player.getItemInHand(hand);
        if (!held.isEmpty()) {
            if (!(level.getBlockEntity(pos) instanceof ServingPlateBlockEntity plate)
                    || !plate.isEmpty() || !ServingPlateService.canServe(held)) return InteractionResult.PASS;
            if (!level.isClientSide && plate.place(held)) {
                if (!player.getAbilities().instabuild) held.shrink(1);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return interactEmptyHand(level, pos, player);
    }
    *///?}

    private static InteractionResult interactEmptyHand(Level level, BlockPos pos, Player player) {
        if (!(level.getBlockEntity(pos) instanceof ServingPlateBlockEntity plate) || plate.isEmpty()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (player.isShiftKeyDown()) {
            ItemStack removed = plate.removeDish();
            if (!removed.isEmpty() && !player.getInventory().add(removed)) {
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.2,
                        pos.getZ() + 0.5, removed);
            }
            return InteractionResult.CONSUME;
        }
        return level instanceof ServerLevel server && ServingPlateService.feedPlayer(server, player, plate)
                ? InteractionResult.CONSUME : InteractionResult.PASS;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement,
                         boolean movedByPiston) {
        if (!state.is(replacement.getBlock()) && level.getBlockEntity(pos) instanceof ServingPlateBlockEntity plate) {
            ItemStack remaining = plate.removeDish();
            if (!remaining.isEmpty()) Containers.dropItemStack(
                    level, pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5, remaining);
        }
        super.onRemove(state, level, pos, replacement, movedByPiston);
    }
}
