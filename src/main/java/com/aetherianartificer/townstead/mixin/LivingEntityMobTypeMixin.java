package com.aetherianartificer.townstead.mixin;

import org.spongepowered.asm.mixin.Mixin;

/**
 * Overrides {@code getMobType} so an entity with an {@code entity_group} gene counts
 * as that creature group (undead/arthropod/...), letting vanilla apply Smite/Bane
 * bonuses, harming-potion healing, and undead-targeting for free. 1.20.1 only:
 * {@code getMobType} was removed in 1.21.1, so this class is empty there and the
 * 1.21.1 path layers the effects in elsewhere.
 */
@Mixin(net.minecraft.world.entity.LivingEntity.class)
public abstract class LivingEntityMobTypeMixin {

    //? if <1.21 {
    /*@org.spongepowered.asm.mixin.injection.Inject(method = "m_6336_", at = @org.spongepowered.asm.mixin.injection.At("RETURN"), cancellable = true, remap = false)
    private void townstead$mobType(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<net.minecraft.world.entity.MobType> cir) {
        net.minecraft.world.entity.LivingEntity self = (net.minecraft.world.entity.LivingEntity) (Object) this;
        if (self.level().isClientSide) return;
        net.minecraft.world.entity.MobType type = com.aetherianartificer.townstead.origin.EntityGroups.mobType(self);
        if (type != null) cir.setReturnValue(type);
    }
    *///?}
}
