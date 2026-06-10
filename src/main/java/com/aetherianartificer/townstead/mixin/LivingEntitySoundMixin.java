package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.habitus.sound.SoundSpec;
import com.aetherianartificer.townstead.origin.gene.types.CustomSoundGeneType.Slot;
import com.aetherianartificer.townstead.origin.sound.CustomSounds;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets a {@code custom_sound} gene replace the bearer's hurt and death sounds (Apugli's
 * {@code custom_hurt_sound} / {@code custom_death_sound}). Both methods are resolved
 * server-side and broadcast, so the replacement is heard by everyone. SRG names on the
 * 1.20.1 Forge build ({@code m_7975_} = getHurtSound, {@code m_5592_} = getDeathSound).
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntitySoundMixin {

    //? if neoforge {
    @Inject(method = "getHurtSound", at = @At("HEAD"), cancellable = true)
    //?} else {
    /*@Inject(method = "m_7975_", at = @At("HEAD"), cancellable = true, remap = false)
    *///?}
    private void townstead$hurtSound(DamageSource source, CallbackInfoReturnable<SoundEvent> cir) {
        SoundSpec.Entry entry = CustomSounds.pick((LivingEntity) (Object) this, Slot.HURT);
        if (entry != null) cir.setReturnValue(entry.sound());
    }

    //? if neoforge {
    @Inject(method = "getDeathSound", at = @At("HEAD"), cancellable = true)
    //?} else {
    /*@Inject(method = "m_5592_", at = @At("HEAD"), cancellable = true, remap = false)
    *///?}
    private void townstead$deathSound(CallbackInfoReturnable<SoundEvent> cir) {
        SoundSpec.Entry entry = CustomSounds.pick((LivingEntity) (Object) this, Slot.DEATH);
        if (entry != null) cir.setReturnValue(entry.sound());
    }
}
