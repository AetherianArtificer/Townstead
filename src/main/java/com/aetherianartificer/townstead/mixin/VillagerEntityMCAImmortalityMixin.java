package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import net.conczin.mca.entity.VillagerEntityMCA;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cancels {@code setAge(int)} on immortal villagers so their vanilla age field
 * stops advancing. The Phase 4 {@code setAgeState} mixin also pins them at
 * their recorded stage; this stops dimension interpolation / love-mode
 * countdowns from drifting underneath.
 *
 * <p>Calendar date of birth keeps incrementing through the calendar tick
 * because it's stored on {@code Life} (not the vanilla age field), so a
 * villager can stay biologically 20 while their displayed age climbs into
 * the thousands.</p>
 *
 * <p>Target method: vanilla {@code Mob.setAge(int)}. On 1.20.1 Forge the SRG
 * name is {@code m_146762_}; on 1.21.1 NeoForge it's still {@code setAge}.
 * {@code remap=false} because we provide the literal name per branch.</p>
 */
@Mixin(VillagerEntityMCA.class)
public abstract class VillagerEntityMCAImmortalityMixin {

    //? if neoforge {
    @Inject(method = "setAge", at = @At("HEAD"), cancellable = true)
    //?} else {
    /*@Inject(method = "m_146762_", at = @At("HEAD"), cancellable = true, remap = false)
    *///?}
    private void townstead$skipAgeWhenImmortal(int age, CallbackInfo ci) {
        VillagerEntityMCA self = (VillagerEntityMCA) (Object) this;
        if (self.level().isClientSide) return;
        if (TownsteadVillagers.get(self).life().immortal()
                || com.aetherianartificer.townstead.origin.trait.TraitEffects.isImmortal(self)) {
            ci.cancel();
        }
    }
}
