package com.aetherianartificer.townstead.mixin.compat.jep;

import com.aetherianartificer.townstead.client.compat.JepMcaVillagerPreview;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Replaces only JEP's cached vanilla mannequin with MCA's normal villager presentation. */
@Pseudo
@Mixin(targets = "com.mrbysco.justenoughprofessions.VillagerCache", remap = false)
public final class JepVillagerCacheMixin {

    @Inject(method = "getVillagerEntity", at = @At("HEAD"), cancellable = true, require = 0)
    private static void townstead$useMcaPreview(VillagerProfession profession,
            CallbackInfoReturnable<Villager> cir) {
        Villager preview = JepMcaVillagerPreview.get(profession);
        if (preview != null) cir.setReturnValue(preview);
    }

    @Inject(method = "clearCache", at = @At("HEAD"), require = 0)
    private static void townstead$clearMcaPreview(CallbackInfo ci) {
        JepMcaVillagerPreview.clear();
    }
}
