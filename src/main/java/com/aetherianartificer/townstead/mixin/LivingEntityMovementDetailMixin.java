package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.tick.VillagerDetail;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A far villager standing idle skips movement and pushing between its brain ticks. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMovementDetailMixin {

    //? if neoforge {
    @WrapWithCondition(method = "aiStep",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;travel(Lnet/minecraft/world/phys/Vec3;)V"))
    //?} else {
    /*@WrapWithCondition(method = "m_8107_", remap = false,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;m_7023_(Lnet/minecraft/world/phys/Vec3;)V"))
    *///?}
    private boolean townstead$farTravel(LivingEntity self, Vec3 input) {
        return !((Object) this instanceof VillagerEntityMCA villager) || !VillagerDetail.movementResting(villager);
    }

    //? if neoforge {
    @WrapWithCondition(method = "aiStep",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;pushEntities()V"))
    //?} else {
    /*@WrapWithCondition(method = "m_8107_", remap = false,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;m_6138_()V"))
    *///?}
    private boolean townstead$farPush(LivingEntity self) {
        return !((Object) this instanceof VillagerEntityMCA villager) || !VillagerDetail.movementResting(villager);
    }
}
