package com.aetherianartificer.townstead.mixin;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
//? if >=1.21 {
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
//?} else {
/*import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
*///?}
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Make MCA villagers treat closed fence gates the same way they treat
 * closed wooden doors: pathable in principle, opened by the navigation
 * layer on the way through. We rewrite the per-cell path-type
 * classification so a closed {@link FenceGateBlock} returns {@code
 * DOOR_WOOD_CLOSED} instead of {@code FENCE}; the existing pathfinder
 * logic in {@code getPathTypeWithinMobBB} (1.21) / {@code
 * evaluateBlockPathType} (1.20) then converts that to {@code
 * WALKABLE_DOOR} because villagers canOpenDoors. Without the rebadge the
 * pathfinder short-circuits on {@code FENCE} before that conversion ever
 * runs.
 *
 * <p>The mixin class extends {@link NodeEvaluator} (the abstract parent
 * of {@link WalkNodeEvaluator}) so the inherited {@code mob} field is
 * accessible via Java inheritance — Mixin's {@code @Shadow} only walks
 * the target class itself and would fail to locate the field, refusing
 * the whole apply step.
 *
 * <p>Scoped to {@link VillagerEntityMCA} so wild mobs (sheep, cows) keep
 * the existing FENCE classification and don't path into closed gates
 * they can't open. The companion {@link PathNavigationFenceGateMixin}
 * handles the actual gate open/close as the villager traverses the
 * path.
 *
 * <p>Defensive notes: this hook runs in the pathfinder hot path. We
 * reuse a thread-local mutable {@link BlockPos} to avoid GC pressure,
 * and wrap the BlockState read in try/catch so any chunk-boundary or
 * unloaded-region edge case silently falls back to vanilla {@code FENCE}
 * classification rather than propagating an exception into the
 * pathfinder.
 */
@Mixin(WalkNodeEvaluator.class)
public abstract class WalkNodeEvaluatorFenceGateMixin extends NodeEvaluator {
    @Unique
    private static final ThreadLocal<BlockPos.MutableBlockPos> townstead$cursor =
            ThreadLocal.withInitial(BlockPos.MutableBlockPos::new);

    //? if >=1.21 {
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
    //?} else {
    /*@Inject(method = "m_8086_(Lnet/minecraft/world/level/BlockGetter;III)Lnet/minecraft/world/level/pathfinder/BlockPathTypes;",
            at = @At("RETURN"), cancellable = true, remap = false)
    private void townstead$rebadgeFenceGateForVillagers(
            BlockGetter level, int x, int y, int z,
            CallbackInfoReturnable<BlockPathTypes> cir) {
        if (cir.getReturnValue() != BlockPathTypes.FENCE) return;
        if (!(this.mob instanceof VillagerEntityMCA)) return;
        try {
            BlockPos.MutableBlockPos cursor = townstead$cursor.get().set(x, y, z);
            BlockState state = level.getBlockState(cursor);
            if (state.getBlock() instanceof FenceGateBlock && !state.getValue(FenceGateBlock.OPEN)) {
                cir.setReturnValue(BlockPathTypes.DOOR_WOOD_CLOSED);
            }
        } catch (Throwable ignored) {
        }
    }
    *///?}
}
