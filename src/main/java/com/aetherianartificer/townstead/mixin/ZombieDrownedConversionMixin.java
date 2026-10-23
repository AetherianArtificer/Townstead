package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.villager.ZombifiedVillagers;
import net.conczin.mca.entity.ZombieVillagerEntityMCA;
import net.minecraft.world.entity.monster.Zombie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A zombified villager being cured doesn't drown partway through. A cured Drowned becomes a zombie
 * villager in the water, and the cure takes longer than drowning does.
 */
@Mixin(Zombie.class)
public abstract class ZombieDrownedConversionMixin {

    //? if neoforge {
    @Inject(method = "convertsInWater", at = @At("HEAD"), cancellable = true)
    //?} else {
    /*@Inject(method = "m_7593_", remap = false, at = @At("HEAD"), cancellable = true)
    *///?}
    private void townstead$noDrowningWhileCured(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ZombieVillagerEntityMCA zombie && zombie.isConverting()
                && ZombifiedVillagers.carries(zombie)) {
            cir.setReturnValue(false);
        }
    }
}
