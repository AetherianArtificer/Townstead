package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.root.LifeStageProgression;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.BreedableRelationship;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A golden apple fed to a child steps it a quarter of its current life stage on Townstead's calendar.
 * MCA's own tick bump is overridden by the calendar stage, so without this the apple does nothing.
 */
@Mixin(value = BreedableRelationship.class, remap = false)
public abstract class GoldenAppleGrowthMixin {
    //? if neoforge {
    @WrapOperation(method = "handleSpecialCaseGift", at = @At(value = "INVOKE",
            target = "Lnet/conczin/mca/entity/VillagerEntityMCA;ageUp(I)V"), expect = 1)
    //?} else {
    /*@WrapOperation(method = "handleSpecialCaseGift", at = @At(value = "INVOKE",
            target = "Lnet/conczin/mca/entity/VillagerEntityMCA;m_146758_(I)V"), expect = 1)
    *///?}
    private void townstead$goldenAppleQuarterStage(VillagerEntityMCA villager, int seconds, Operation<Void> original) {
        if (!LifeStageProgression.advanceQuarterStage(villager)) original.call(villager, seconds);
    }
}
