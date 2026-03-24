package com.aetherianartificer.townstead.mixin.compat.farmersdelight;

import com.aetherianartificer.townstead.compat.farmersdelight.FarmersDelightPathingHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
//? if >=1.21 {
import net.minecraft.world.level.pathfinder.PathType;
//?} else {
/*import net.minecraft.world.level.pathfinder.BlockPathTypes;
*///?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;

@Pseudo
@Mixin(targets = {
        "vectorwing.farmersdelight.common.block.CookingPotBlock",
        "vectorwing.farmersdelight.common.block.SkilletBlock",
        "vectorwing.farmersdelight.common.block.StoveBlock"
})
public abstract class FarmersDelightCookwarePathMixin {
    @Inject(method = "getBlockPathType", at = @At("HEAD"), cancellable = true, require = 0)
    private void townstead$markCookwareOverStoveAsFire(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            @Nullable Mob entity,
            //? if >=1.21 {
            CallbackInfoReturnable<PathType> cir
            //?} else {
            /*CallbackInfoReturnable<BlockPathTypes> cir
            *///?}
    ) {
        if (FarmersDelightPathingHooks.isHazardousCookware(level, pos)) {
            //? if >=1.21 {
            cir.setReturnValue(PathType.DAMAGE_FIRE);
            //?} else {
            /*cir.setReturnValue(BlockPathTypes.DAMAGE_FIRE);
            *///?}
        }
    }
}
