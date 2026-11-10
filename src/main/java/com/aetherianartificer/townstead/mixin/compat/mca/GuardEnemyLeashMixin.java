package com.aetherianartificer.townstead.mixin.compat.mca;

import com.aetherianartificer.townstead.compat.mcacapitals.CapitalsCampaignBridge;
import com.aetherianartificer.townstead.tick.GuardLeash;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.brain.sensor.GuardEnemiesSensor;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** MCA picks and keeps a guard's foe through this test; a foe past the village leash fails it. */
@Mixin(GuardEnemiesSensor.class)
public abstract class GuardEnemyLeashMixin {
    @Inject(method = "isGuardEnemy", at = @At("RETURN"), cancellable = true, remap = false)
    private static void townstead$leash(LivingEntity entity, LivingEntity guard, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && guard != null && GuardLeash.beyond(guard, entity)) cir.setReturnValue(false);
    }

    //? if neoforge {
    @Inject(method = "isWithinGuardEnemyRange(Lnet/conczin/mca/entity/VillagerEntityMCA;Lnet/minecraft/world/entity/LivingEntity;Ljava/util/Optional;)Z",
            at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void townstead$capitalsCampaignRange(VillagerEntityMCA guard, LivingEntity target, java.util.Optional<?> followed,
                                                 CallbackInfoReturnable<Boolean> cir) {
        if (CapitalsCampaignBridge.withinCampaignRange(guard, target)) cir.setReturnValue(true);
    }
    //?} else {
    /*// This MCA's range check takes the guard and target only. Capitals' Forge build adds far campaign
    // foes itself, and this lets the ones MCA already sees pass the range check too.
    @Inject(method = "isWithinGuardEnemyRange(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/LivingEntity;)Z",
            at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void townstead$capitalsCampaignRange(LivingEntity guard, LivingEntity target,
                                                 CallbackInfoReturnable<Boolean> cir) {
        if (CapitalsCampaignBridge.withinCampaignRange(guard, target)) cir.setReturnValue(true);
    }
    *///?}
}
