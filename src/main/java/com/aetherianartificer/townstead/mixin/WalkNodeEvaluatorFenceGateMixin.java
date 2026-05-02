package com.aetherianartificer.townstead.mixin;

import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.spongepowered.asm.mixin.Mixin;
//? if >=1.21 {
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//?} else {
/*import forge.net.mca.entity.ai.pathfinder.ExtendedPathNodeType;
import forge.net.mca.entity.ai.pathfinder.VillagerLandPathNodeMaker;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
*///?}

/**
 * Make MCA villagers treat closed fence gates the same way they treat
 * closed wooden doors: pathable in principle, opened by the navigation
 * layer on the way through. We rewrite the per-cell path-type
 * classification so a closed {@link FenceGateBlock} returns {@code
 * DOOR_WOOD_CLOSED} instead of {@code FENCE}; the existing pathfinder
 * logic in {@code getPathTypeWithinMobBB} then converts that to {@code
 * WALKABLE_DOOR} because villagers canOpenDoors. Without the rebadge the
 * pathfinder short-circuits on {@code FENCE} before that conversion ever
 * runs.
 *
 * <p>The mixin extends {@link NodeEvaluator} so the inherited {@code
 * mob} field is accessible via Java inheritance — Mixin's {@code
 * @Shadow} only walks the target class itself and would fail to locate
 * the field, refusing the whole apply step.
 *
 * <p>On 1.20.1 Forge, MCA villagers do not use vanilla {@link
 * WalkNodeEvaluator}; MCA 7.6 installs its own {@code
 * VillagerLandPathNodeMaker}. The Forge branch targets that evaluator
 * directly and returns MCA's {@code WALKABLE_DOOR} extended type for
 * closed fence gates, preserving the same door-opening flow without
 * relying on fragile inherited-field shadowing.
 */
//? if >=1.21 {
@Mixin(WalkNodeEvaluator.class)
//?} else {
/*@Mixin(VillagerLandPathNodeMaker.class)
*///?}
public abstract class WalkNodeEvaluatorFenceGateMixin extends NodeEvaluator {
    //? if >=1.21 {
    @Unique
    private static final ThreadLocal<BlockPos.MutableBlockPos> townstead$cursor =
            ThreadLocal.withInitial(BlockPos.MutableBlockPos::new);

    @Inject(method = "getPathType(Lnet/minecraft/world/level/pathfinder/PathfindingContext;III)Lnet/minecraft/world/level/pathfinder/PathType;",
            at = @At("RETURN"), cancellable = true)
    private void townstead$rebadgeFenceGateForVillagers(
            PathfindingContext context, int x, int y, int z,
            CallbackInfoReturnable<PathType> cir) {
        if (cir.getReturnValue() != PathType.FENCE) return;
        if (!(this.mob instanceof VillagerEntityMCA)) return;
        try {
            BlockPos.MutableBlockPos cursor = townstead$cursor.get().set(x, y, z);
            BlockState state = context.getBlockState(cursor);
            if (state.getBlock() instanceof FenceGateBlock && !state.getValue(FenceGateBlock.OPEN)) {
                cir.setReturnValue(PathType.DOOR_WOOD_CLOSED);
            }
        } catch (Throwable ignored) {
        }
    }
    //?}
    //? if forge {
    /*@Inject(method = "getCommonNodeType", remap = false, at = @At("RETURN"), cancellable = true)
    private static void townstead$rebadgeMcaFenceGateForVillagers(
            BlockGetter level, BlockPos pos,
            CallbackInfoReturnable<ExtendedPathNodeType> cir) {
        ExtendedPathNodeType type = cir.getReturnValue();
        if (type != ExtendedPathNodeType.FENCE && type != ExtendedPathNodeType.BLOCKED) return;
        try {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof FenceGateBlock && !state.getValue(FenceGateBlock.OPEN)) {
                cir.setReturnValue(ExtendedPathNodeType.DOOR_WOOD_CLOSED);
            }
        } catch (Throwable ignored) {
        }
    }

    @Inject(method = "adjustNodeType", remap = false, at = @At("RETURN"), cancellable = true)
    private void townstead$makeMcaFenceGateWalkableDoor(
            BlockGetter level, boolean canOpenDoors, boolean canEnterOpenDoors, BlockPos pos,
            ExtendedPathNodeType nodeType, CallbackInfoReturnable<ExtendedPathNodeType> cir) {
        ExtendedPathNodeType type = cir.getReturnValue();
        if (type != ExtendedPathNodeType.FENCE
                && type != ExtendedPathNodeType.BLOCKED
                && type != ExtendedPathNodeType.DOOR_WOOD_CLOSED) {
            return;
        }
        try {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof FenceGateBlock && !state.getValue(FenceGateBlock.OPEN)) {
                cir.setReturnValue(ExtendedPathNodeType.WALKABLE_DOOR);
            }
        } catch (Throwable ignored) {
        }
    }
    *///?}
}
