package com.aetherianartificer.townstead.mixin;

import org.spongepowered.asm.mixin.Mixin;

/**
 * 1.21.1 potion-inversion parity for {@code entity_group}: forces
 * {@code isInvertedHealAndHarm} true for undead-gene entities, so healing potions
 * hurt them and harming potions heal them (mirroring what the 1.20.1 getMobType
 * mixin gets for free). Empty on 1.20.1, where getMobType already drives this.
 */
@Mixin(net.minecraft.world.entity.LivingEntity.class)
public abstract class LivingEntityInvertedHealMixin {

    //? if >=1.21 {
    @org.spongepowered.asm.mixin.injection.Inject(method = "isInvertedHealAndHarm", at = @org.spongepowered.asm.mixin.injection.At("RETURN"), cancellable = true)
    private void townstead$invertForUndead(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) return;
        net.minecraft.world.entity.LivingEntity self = (net.minecraft.world.entity.LivingEntity) (Object) this;
        if (self.level().isClientSide) return;
        if (com.aetherianartificer.townstead.origin.EntityGroups.isUndead(self)) cir.setReturnValue(true);
    }
    //?}
}
