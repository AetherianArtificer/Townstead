package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.replace.MobReplacer;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * MCA walks homeless villagers to the floor beside the nearest beds at night. A wild villager stays
 * out of houses: it still acts as the mob it replaced, and every one nearby would pick the same bedside.
 * Pseudo keeps this inert on MCA builds without the task.
 */
@Pseudo
@Mixin(targets = "net.conczin.mca.entity.ai.brain.tasks.SeekIndoorShelterTask", remap = false)
public abstract class SeekIndoorShelterWildMixin {
    @Inject(method = "checkExtraStartConditions(Lnet/minecraft/server/level/ServerLevel;Lnet/conczin/mca/entity/VillagerEntityMCA;)Z",
            at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void townstead$wildStaysOutside(ServerLevel level, VillagerEntityMCA villager, CallbackInfoReturnable<Boolean> cir) {
        if (MobReplacer.isWild(villager)) cir.setReturnValue(false);
    }
}
