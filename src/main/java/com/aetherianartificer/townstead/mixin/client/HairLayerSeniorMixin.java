package com.aetherianartificer.townstead.mixin.client;

import com.aetherianartificer.townstead.client.skin.SeniorHairDesat;
import net.conczin.mca.client.render.layer.HairLayer;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lerps a senior villager's hair colour toward grey by a sigmoid of how far
 * they are through the senior stage (data syncs from {@link com.aetherianartificer.townstead.calendar.VillagerLifeSyncPayload}).
 * Pass-through for everyone else.
 *
 * <p>The return type of MCA's {@code HairLayer.getColor} differs by version:
 * {@code int} ARGB on 1.21.1, {@code float[]} on 1.20.1. {@code remap=false}
 * because the method lives on MCA, not vanilla.</p>
 */
@Mixin(HairLayer.class)
public abstract class HairLayerSeniorMixin<T extends LivingEntity, M extends HumanoidModel<T>> {

    //? if neoforge {
    @Inject(method = "getColor", remap = false, at = @At("RETURN"), cancellable = true, require = 1)
    private void townstead$desaturateHair(T entity, float partialTick, CallbackInfoReturnable<Integer> cir) {
        if (!(entity instanceof VillagerEntityMCA villager)) return;
        float factor = SeniorHairDesat.lerpFactor(villager);
        if (factor <= 0f) return;
        cir.setReturnValue(SeniorHairDesat.applyArgb(cir.getReturnValue(), factor));
    }
    //?} else {
    /*@Inject(method = "getColor", remap = false, at = @At("RETURN"), cancellable = true, require = 1)
    private void townstead$desaturateHair(T entity, float partialTick, CallbackInfoReturnable<float[]> cir) {
        if (!(entity instanceof VillagerEntityMCA villager)) return;
        float factor = SeniorHairDesat.lerpFactor(villager);
        if (factor <= 0f) return;
        cir.setReturnValue(SeniorHairDesat.applyFloats(cir.getReturnValue(), factor));
    }
    *///?}
}
