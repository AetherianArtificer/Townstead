package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.chronicle.emit.InfectionWatcher;
import net.conczin.mca.entity.VillagerEntityMCA;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Reports infection changes to the Chronicle. The progress is read before and after MCA stores it, so a
 * villager loaded mid-infection does not count as a new bite. A set that the immunity gene cancels never
 * reaches the return.
 */
@Mixin(VillagerEntityMCA.class)
public abstract class VillagerInfectionChronicleMixin {
    @Unique private float townstead$infectionBefore;

    @Inject(method = "setInfectionProgress", at = @At("HEAD"), remap = false)
    private void townstead$readInfection(float progress, CallbackInfo ci) {
        townstead$infectionBefore = ((VillagerEntityMCA) (Object) this).getInfectionProgress();
    }

    @Inject(method = "setInfectionProgress", at = @At("RETURN"), remap = false)
    private void townstead$reportInfection(float progress, CallbackInfo ci) {
        VillagerEntityMCA self = (VillagerEntityMCA) (Object) this;
        InfectionWatcher.onProgress(self, townstead$infectionBefore, self.getInfectionProgress());
    }
}
