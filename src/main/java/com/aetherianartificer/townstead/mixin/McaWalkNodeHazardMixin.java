package com.aetherianartificer.townstead.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
//? if >=1.21 {
import com.aetherianartificer.townstead.root.hazard.HazardPaths;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.PathType;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//?}

/**
 * MCA's evaluator answers single-block checks (strafing, approach spots) without
 * getPathTypeOfMob, so {@link WalkNodeHazardMixin} does not see them. Same rule here.
 */
@Pseudo
@Mixin(targets = "net.conczin.mca.entity.ai.navigation.MCAWalkNodeEvaluator", remap = false)
public abstract class McaWalkNodeHazardMixin {
    //? if >=1.21 {
    @Inject(method = "getPathType(Lnet/minecraft/world/entity/Mob;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/pathfinder/PathType;",
            at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private void townstead$hazardCost(Mob mob, BlockPos pos, CallbackInfoReturnable<PathType> cir) {
        if (cir.getReturnValue() != PathType.BLOCKED
                && com.aetherianartificer.townstead.storage.PrivatePaths.closed(mob, pos.getX(), pos.getY(), pos.getZ())) {
            cir.setReturnValue(PathType.BLOCKED);
            return;
        }
        if (cir.getReturnValue() == PathType.WALKABLE
                && HazardPaths.dangerous(mob, pos.getX(), pos.getY(), pos.getZ())) {
            cir.setReturnValue(PathType.DANGER_OTHER);
        }
    }
    //?}
}
