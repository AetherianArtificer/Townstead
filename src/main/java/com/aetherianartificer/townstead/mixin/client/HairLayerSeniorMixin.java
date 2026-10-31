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
 * Pass-through for everyone else. Also records the final colour into
 * {@link com.aetherianartificer.townstead.client.species.RigHairColor} — the
 * one place both versions resolve hair — so hair-tinted attachments can match.
 *
 * <p>The return type of MCA's {@code HairLayer.getColor} differs by version:
 * {@code int} ARGB on 1.21.1, {@code float[]} on 1.20.1. {@code remap=false}
 * because the method lives on MCA, not vanilla.</p>
 */
@Mixin(HairLayer.class)
public abstract class HairLayerSeniorMixin<T extends LivingEntity, M extends HumanoidModel<T>> {

    //? if neoforge {
    @Inject(method = "getColor", remap = false, at = @At("RETURN"), cancellable = true, expect = 1)
    private void townstead$desaturateHair(T entity, float partialTick, CallbackInfoReturnable<Integer> cir) {
        int argb = cir.getReturnValue();
        if (entity instanceof VillagerEntityMCA villager) {
            float factor = SeniorHairDesat.lerpFactor(villager);
            if (factor > 0f) {
                argb = SeniorHairDesat.applyArgb(argb, factor);
                cir.setReturnValue(argb);
            }
        }
        com.aetherianartificer.townstead.client.species.RigHairColor.put(entity.getId(), argb & 0xFFFFFF);
    }
    //?} else {
    /*@Inject(method = "getColor", remap = false, at = @At("RETURN"), cancellable = true, expect = 1)
    private void townstead$desaturateHair(T entity, float partialTick, CallbackInfoReturnable<float[]> cir) {
        float[] color = cir.getReturnValue();
        if (entity instanceof VillagerEntityMCA villager) {
            float factor = SeniorHairDesat.lerpFactor(villager);
            if (factor > 0f) {
                color = SeniorHairDesat.applyFloats(color, factor);
                cir.setReturnValue(color);
            }
        }
        if (color != null && color.length >= 3) {
            int rgb = ((int) (Math.min(1f, Math.max(0f, color[0])) * 255f) << 16)
                    | ((int) (Math.min(1f, Math.max(0f, color[1])) * 255f) << 8)
                    | (int) (Math.min(1f, Math.max(0f, color[2])) * 255f);
            com.aetherianartificer.townstead.client.species.RigHairColor.put(entity.getId(), rgb);
        }
    }
    *///?}
}
