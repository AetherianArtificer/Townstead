package com.aetherianartificer.townstead.mixin.client;

import com.aetherianartificer.townstead.client.species.BuildProportions;
import net.conczin.mca.client.model.VillagerEntityBaseModelMCA;
import net.conczin.mca.entity.VillagerLike;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies a race's per-part build proportions to MCA's villager model; see {@link BuildProportions}.
 * A villager-mode player is drawn with MCA's player model instead, which
 * {@link PlayerEntityExtendedModelAnimationMixin} proportions the same way.
 */
@Mixin(VillagerEntityBaseModelMCA.class)
public abstract class VillagerHeadProportionMixin<T extends LivingEntity & VillagerLike<T>> {

    //? if neoforge {
    @Inject(method = "setupAnim", remap = false, at = @At("TAIL"), expect = 1)
    //?} else {
    /*@Inject(method = "m_6973_", remap = false, at = @At("TAIL"), expect = 1)
    *///?}
    private void townstead$proportionParts(T entity, float limbAngle, float limbDistance,
            float animationProgress, float headYaw, float headPitch, CallbackInfo ci) {
        BuildProportions.apply((HumanoidModel<?>) (Object) this, entity, entity.getGenetics(), entity.getTraits());
    }
}
