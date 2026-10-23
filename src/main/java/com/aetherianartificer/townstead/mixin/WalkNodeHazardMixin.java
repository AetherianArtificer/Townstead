package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.root.hazard.HazardPaths;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A block where a mob's own gene would hurt it (see {@code Hazard}) reads as other known danger, so
 * routes go around it when they reasonably can and still cross it when nothing else reaches.
 * MCA's evaluator inherits this method, so villagers are covered.
 */
@Mixin(WalkNodeEvaluator.class)
public abstract class WalkNodeHazardMixin {

    //? if neoforge {
    @Inject(method = "getPathTypeOfMob", at = @At("RETURN"), cancellable = true)
    private void townstead$hazardCost(net.minecraft.world.level.pathfinder.PathfindingContext context,
                                      int x, int y, int z, Mob mob,
                                      CallbackInfoReturnable<net.minecraft.world.level.pathfinder.PathType> cir) {
        if (cir.getReturnValue() == net.minecraft.world.level.pathfinder.PathType.WALKABLE
                && HazardPaths.dangerous(mob, x, y, z)) {
            cir.setReturnValue(net.minecraft.world.level.pathfinder.PathType.DANGER_OTHER);
        }
    }
    //?} else {
    /*@Inject(method = "m_7209_", remap = false, at = @At("RETURN"), cancellable = true)
    private void townstead$hazardCost(net.minecraft.world.level.BlockGetter level,
                                      int x, int y, int z, Mob mob,
                                      CallbackInfoReturnable<net.minecraft.world.level.pathfinder.BlockPathTypes> cir) {
        if (cir.getReturnValue() == net.minecraft.world.level.pathfinder.BlockPathTypes.WALKABLE
                && HazardPaths.dangerous(mob, x, y, z)) {
            cir.setReturnValue(net.minecraft.world.level.pathfinder.BlockPathTypes.DANGER_OTHER);
        }
    }
    *///?}
}
