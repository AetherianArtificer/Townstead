package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.origin.gene.types.CustomSoundGeneType.Slot;
import com.aetherianartificer.townstead.origin.sound.CustomSounds;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.registry.SoundsMCA;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Gives an MCA villager its origin's full voice. A {@code custom_sound} gene maps voice slots to
 * sounds; we map every MCA villager sound onto a slot and, when one is set, play it ourselves
 * ({@link CustomSounds#handleCustom}, honouring the sound's volume, pitch and {@code chance}) and
 * suppress MCA's own. For the getters (ambient/hurt/death/yes/no/surprise) that means returning
 * MCA's SILENT so MCA's follow-up {@code playSound} is inaudible; for the methods MCA plays with a
 * raw {@code playSound} (greet, celebrate) we cancel. Playing in plain code keeps every vanilla call
 * out of the mixin, which the no-refmap 1.20.1 Forge build can't remap. So a skeletownie rattles for
 * everything, as often (chance) and as loud/deep (volume/pitch) as the gene says, each in its own pitch.
 *
 * <p>1.20.1 Forge SRG (vanilla-override methods, {@code remap = false}): {@code m_7515_}
 * getAmbientSound, {@code m_7975_} getHurtSound, {@code m_5592_} getDeathSound, {@code m_7596_}
 * getNotifyTradeSound, {@code m_35310_} playCelebrateSound. The rest (getNoSound, getSurprisedSound,
 * playWelcomeSound) are MCA-only methods, so the literal name + {@code remap = false} fits both.</p>
 */
@Mixin(VillagerEntityMCA.class)
public abstract class VillagerSoundMixin {

    //? if neoforge {
    @Inject(method = "getAmbientSound", at = @At("HEAD"), cancellable = true, require = 1)
    //?} else {
    /*@Inject(method = "m_7515_", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    *///?}
    private void townstead$ambient(CallbackInfoReturnable<SoundEvent> cir) { townstead$voice(Slot.AMBIENT, cir); }

    //? if neoforge {
    @Inject(method = "getHurtSound", at = @At("HEAD"), cancellable = true, require = 1)
    //?} else {
    /*@Inject(method = "m_7975_", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    *///?}
    private void townstead$hurt(DamageSource source, CallbackInfoReturnable<SoundEvent> cir) { townstead$voice(Slot.HURT, cir); }

    //? if neoforge {
    @Inject(method = "getDeathSound", at = @At("HEAD"), cancellable = true, require = 1)
    //?} else {
    /*@Inject(method = "m_5592_", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    *///?}
    private void townstead$death(CallbackInfoReturnable<SoundEvent> cir) { townstead$voice(Slot.DEATH, cir); }

    //? if neoforge {
    @Inject(method = "getNotifyTradeSound", at = @At("HEAD"), cancellable = true, require = 1)
    //?} else {
    /*@Inject(method = "m_7596_", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    *///?}
    private void townstead$yes(CallbackInfoReturnable<SoundEvent> cir) { townstead$voice(Slot.YES, cir); }

    @Inject(method = "getNoSound", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void townstead$no(CallbackInfoReturnable<SoundEvent> cir) { townstead$voice(Slot.NO, cir); }

    @Inject(method = "getSurprisedSound", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void townstead$surprise(CallbackInfoReturnable<SoundEvent> cir) { townstead$voice(Slot.SURPRISE, cir); }

    @Inject(method = "playWelcomeSound", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void townstead$greet(CallbackInfo ci) {
        if (CustomSounds.handleCustom((LivingEntity) (Object) this, Slot.GREET)) ci.cancel();
    }

    //? if neoforge {
    @Inject(method = "playCelebrateSound", at = @At("HEAD"), cancellable = true, require = 1)
    //?} else {
    /*@Inject(method = "m_35310_", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    *///?}
    private void townstead$celebrate(CallbackInfo ci) {
        if (CustomSounds.handleCustom((LivingEntity) (Object) this, Slot.CELEBRATE)) ci.cancel();
    }

    private void townstead$voice(Slot slot, CallbackInfoReturnable<SoundEvent> cir) {
        if (CustomSounds.handleCustom((LivingEntity) (Object) this, slot)) cir.setReturnValue(townstead$silent());
    }

    /** MCA's silent sound, returned to mute MCA's own follow-up play (Forge wraps it in a supplier). */
    private static SoundEvent townstead$silent() {
        //? if neoforge {
        return SoundsMCA.SILENT;
        //?} else {
        /*return SoundsMCA.SILENT.get();
        *///?}
    }
}
