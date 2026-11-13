package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.root.attachment.BodyFit;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Residency;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A home whose way in doesn't fit its villager's volume isn't a home it can use, so MCA's search moves
 * on rather than leaving them stuck at the door. Optional: older MCA builds choose homes differently.
 */
@Mixin(value = Residency.class, remap = false)
public abstract class ResidencyBodyFitMixin {

    @Shadow @Final private VillagerEntityMCA entity;

    @Inject(method = "canReachBed", at = @At("RETURN"), cancellable = true, require = 0)
    private void townstead$bedMustFit(ServerLevel level, BlockPos home, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue() && !BodyFit.canReach(entity, home)) cir.setReturnValue(false);
    }
}
