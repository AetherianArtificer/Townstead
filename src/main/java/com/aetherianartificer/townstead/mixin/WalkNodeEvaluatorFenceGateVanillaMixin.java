package com.aetherianartificer.townstead.mixin;

import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.spongepowered.asm.mixin.Mixin;
//? if forge {
/*import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
*///?}

/**
 * 1.20.1 companion to {@link WalkNodeEvaluatorFenceGateMixin} for MCA builds that
 * pathfind through the vanilla evaluator. MCA 7.6.27+ (the 1.20.1 backport line)
 * deleted {@code VillagerLandPathNodeMaker} and navigates with an
 * {@code MCAWalkNodeEvaluator} that extends vanilla {@link WalkNodeEvaluator}
 * without overriding classification, so the fence-gate rebadge has to live on the
 * vanilla method there. On MCA &le;7.6.26 villagers never touch the vanilla
 * evaluator (their maker extends {@code NodeEvaluator} directly), so this hook is
 * inert and the {@code VillagerLandPathNodeMaker} hooks in the companion class
 * keep covering them.
 *
 * <p>{@code m_264405_} = {@code evaluateBlockPathType(BlockGetter, BlockPos,
 * BlockPathTypes)}, the per-cell step where vanilla converts
 * {@code DOOR_WOOD_CLOSED} to {@code WALKABLE_DOOR}. That conversion runs before
 * a RETURN injection can rebadge, so this sets {@code WALKABLE_DOOR} directly;
 * MCA's navigation enables canOpenDoors, and gates are physically opened by
 * {@link PathNavigationFenceGateMixin} during traversal. On 1.21 the equivalent
 * hook lives in {@link WalkNodeEvaluatorFenceGateMixin}, so this class is an
 * empty mixin there.
 */
//? if forge {
/*@Mixin(WalkNodeEvaluator.class)
public abstract class WalkNodeEvaluatorFenceGateVanillaMixin extends NodeEvaluator {

    @Inject(method = "m_264405_", remap = false, at = @At("RETURN"), cancellable = true)
    private void townstead$rebadgeFenceGateForMcaVillagers(
            BlockGetter level, BlockPos pos, BlockPathTypes type,
            CallbackInfoReturnable<BlockPathTypes> cir) {
        BlockPathTypes out = cir.getReturnValue();
        // Closed fence gates classify as BLOCKED (via isPathfindable); FENCE kept
        // to match the ExtendedPathNodeType handling in the companion mixin.
        if (out != BlockPathTypes.BLOCKED && out != BlockPathTypes.FENCE) return;
        if (!(this.mob instanceof VillagerEntityMCA)) return;
        try {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof FenceGateBlock && !state.getValue(FenceGateBlock.OPEN)) {
                cir.setReturnValue(BlockPathTypes.WALKABLE_DOOR);
            }
        } catch (Throwable ignored) {
        }
    }
}
*///?} else {
@Mixin(WalkNodeEvaluator.class)
public abstract class WalkNodeEvaluatorFenceGateVanillaMixin {
}
//?}
