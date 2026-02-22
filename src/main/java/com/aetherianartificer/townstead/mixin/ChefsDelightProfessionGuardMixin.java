package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.compat.chefsdelight.ChefsDelightCompat;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.world.entity.npc.VillagerProfession;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VillagerEntityMCA.class)
public abstract class ChefsDelightProfessionGuardMixin {
    @Inject(method = "setProfession", at = @At("HEAD"), cancellable = true)
    private void townstead$blockChefsDelightProfession(VillagerProfession profession, CallbackInfo ci) {
        if (ChefsDelightCompat.shouldBlockProfession(profession)) {
            ci.cancel();
        }
    }
}
