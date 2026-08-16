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
//?}

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
 * <p>On the current 1.20.1 backport, the equivalent SRG-named hook lives in
 * {@link WalkNodeEvaluatorFenceGateVanillaMixin}. This class stays as an empty
 * mixin on that branch so the shared mixin list remains stable.
 */
//? if >=1.21 {
@Mixin(WalkNodeEvaluator.class)
//?} else {
/*@Mixin(WalkNodeEvaluator.class)
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
}
