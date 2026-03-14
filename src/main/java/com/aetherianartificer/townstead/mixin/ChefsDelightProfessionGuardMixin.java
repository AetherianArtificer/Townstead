package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.compat.chefsdelight.ChefsDelightCompat;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VillagerEntityMCA.class)
public abstract class ChefsDelightProfessionGuardMixin extends Villager {
    private ChefsDelightProfessionGuardMixin() { super(null, null); }

    @Inject(method = "setProfession", remap = false, at = @At("HEAD"), cancellable = true)
    private void townstead$blockChefsDelightProfession(VillagerProfession profession, CallbackInfo ci) {
        if (ChefsDelightCompat.shouldBlockProfession(profession)) {
            ci.cancel();
        }
    }

    //? if neoforge {
    @Inject(method = "setVillagerData", at = @At("HEAD"), cancellable = true)
    //?} else {
    /*@Inject(method = "m_34375_", remap = false, at = @At("HEAD"), cancellable = true)
    *///?}
    private void townstead$blockChefsDelightVillagerData(VillagerData data, CallbackInfo ci) {
        if (ChefsDelightCompat.shouldBlockProfession(data.getProfession())) {
            ci.cancel();
        }
    }
}
