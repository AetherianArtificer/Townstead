package com.aetherianartificer.townstead.mixin;

import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.spongepowered.asm.mixin.Mixin;
//? if forge {
/*import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
*///?}

/**
 * Fence-gate and barrier pathfinding for MCA builds that navigate through the vanilla
 * evaluator. MCA 7.6.27+ (the 1.20.1 backport line) deleted
 * {@code VillagerLandPathNodeMaker} and navigates with an {@code MCAWalkNodeEvaluator}
 * that extends vanilla {@link WalkNodeEvaluator} without overriding classification, so
 * both hooks below have to live on the vanilla methods there. On MCA &le;7.6.26
 * villagers never touch the vanilla evaluator (their maker extends
 * {@code NodeEvaluator} directly), so they are inert and the
 * {@code VillagerLandPathNodeMaker} hooks in the companion class keep covering them.
 *
 * <p>Both hooks are 1.20.1 only. On 1.21.1, MCA 7.7.37+ does all of this itself inside
 * {@code MCAWalkNodeEvaluator}, gated on its {@code villagersInteractWithFenceGates}
 * config, so this is an empty mixin there rather than a second opinion that would
 * override that config.
 *
 * <p>{@code m_264405_} = {@code evaluateBlockPathType(BlockGetter, BlockPos,
 * BlockPathTypes)}, the per-cell step where vanilla converts
 * {@code DOOR_WOOD_CLOSED} to {@code WALKABLE_DOOR}. That conversion runs before
 * a RETURN injection can rebadge, so this sets {@code WALKABLE_DOOR} directly;
 * MCA's navigation enables canOpenDoors, and gates are physically opened by
 * {@link PathNavigationFenceGateMixin} during traversal.
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

    // 1.20.1 parity with MCA 7.7.37's rejectBlockedRaisedBarrierTransitions: villagers kept
    // trying to walk over cobblestone walls and fences, most visibly as getting stuck beside
    // stairs. A fence, wall, or closed gate collides to 1.5 blocks, well over the 0.6 step
    // height, so a neighbour standing on top of one is never reachable by stepping up and
    // only ever produces a path the villager stalls on.
    //
    // Only upward transitions are dropped, so a villager already on top of a wall can still
    // walk along it, and an open gate is not a barrier: the companion rebadge above routes
    // villagers straight through those.
    //
    // m_6065_ = getNeighbors(Node[], Node). It is declared on NodeEvaluator and overridden
    // by WalkNodeEvaluator, which is the override this targets.
    @Inject(method = "m_6065_", remap = false, at = @At("RETURN"), cancellable = true)
    private void townstead$rejectBarrierTopTransitions(
            Node[] nodes, Node origin, CallbackInfoReturnable<Integer> cir) {
        if (!(this.mob instanceof VillagerEntityMCA)) return;
        int count = cir.getReturnValue();
        if (count <= 0) return;

        int writeIndex = 0;
        for (int readIndex = 0; readIndex < count; readIndex++) {
            Node candidate = nodes[readIndex];
            if (candidate == null) continue;
            if (candidate.y > origin.y && townstead$standsOnBarrier(candidate)) continue;
            nodes[writeIndex++] = candidate;
        }
        if (writeIndex == count) return;
        for (int i = writeIndex; i < count; i++) {
            nodes[i] = null;
        }
        cir.setReturnValue(writeIndex);
    }

    private boolean townstead$standsOnBarrier(Node candidate) {
        try {
            BlockState state = this.level.getBlockState(
                    new BlockPos(candidate.x, candidate.y - 1, candidate.z));
            if (state.getBlock() instanceof FenceGateBlock) {
                return !state.getValue(FenceGateBlock.OPEN);
            }
            return state.is(BlockTags.FENCES) || state.is(BlockTags.WALLS);
        } catch (Throwable ignored) {
            return false;
        }
    }
}
*///?} else {
@Mixin(WalkNodeEvaluator.class)
public abstract class WalkNodeEvaluatorFenceGateVanillaMixin {
}
//?}
