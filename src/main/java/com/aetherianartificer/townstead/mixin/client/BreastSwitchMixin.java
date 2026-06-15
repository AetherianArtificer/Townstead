package com.aetherianartificer.townstead.mixin.client;

import com.aetherianartificer.townstead.client.species.RigModels;
import net.conczin.mca.client.model.CommonVillagerModel;
import net.conczin.mca.client.model.VillagerEntityBaseModelMCA;
import net.conczin.mca.entity.VillagerLike;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Per-species breast switch. MCA's {@code setupAnim} calls {@code applyVillagerDimensions}, which
 * makes the breast part visible for female villagers, on both the body model and the villager-shaped
 * armor model (both are {@link VillagerEntityBaseModelMCA}). For a species whose data sets
 * {@code "breasts": false} (e.g. skeletownies) we hide it right after, so the host armor renders
 * flat-chested without losing the armor. The villager IS the entity here; the player's armor model is
 * a separate class fed a throwaway proxy, so the player is left to MCA's default for now.
 */
@Mixin(VillagerEntityBaseModelMCA.class)
public abstract class BreastSwitchMixin<T extends LivingEntity & VillagerLike<T>> {

    //? if neoforge {
    @Inject(method = "setupAnim", remap = false, at = @At("TAIL"))
    //?} else {
    /*@Inject(method = "m_6973_", remap = false, at = @At("TAIL"))
    *///?}
    private void townstead$breastSwitch(T entity, float limbAngle, float limbDistance, float animationProgress,
                                        float headYaw, float headPitch, CallbackInfo ci) {
        if (!RigModels.breasts(entity)) {
            ((CommonVillagerModel<?>) (Object) this).getBreastPart().visible = false;
        }
    }
}
