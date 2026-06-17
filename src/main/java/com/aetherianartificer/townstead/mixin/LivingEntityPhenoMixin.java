package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.origin.hook.PhenoHooks;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Pheno interception points that live on {@link LivingEntity}. The growth point for any future
 * {@code LivingEntity}-method modifier hook. All logic is in {@link PhenoHooks}; these injectors
 * only forward.
 *
 * <p>{@code status_effect_duration/amplifier}: rewrites the incoming effect on
 * {@code addEffect(MobEffectInstance, Entity)} ({@code m_147207_} on 1.20.1), which the single-arg
 * overload also routes through. There is no event that lets you rescale an effect before it is
 * applied.</p>
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityPhenoMixin {

    //? if neoforge {
    @ModifyVariable(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("HEAD"), argsOnly = true)
    //?} else {
    /*@ModifyVariable(method = "m_147207_(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("HEAD"), argsOnly = true, remap = false)
    *///?}
    private MobEffectInstance townstead$scaleEffect(MobEffectInstance effect) {
        if (effect == null) return null;
        return PhenoHooks.scaleEffect((LivingEntity) (Object) this, effect);
    }
}
