package com.aetherianartificer.townstead.mixin.compat.thirst;

import com.aetherianartificer.townstead.compat.thirst.DataDrivenThirstCompat;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native tooltip and consumption both obtain their droplet count here. */
@Pseudo
@Mixin(targets = "toughasnails.init.ModTags$Items", remap = false)
public class TanDrinkValuesMixin {
    @Inject(method = "getThirstRestored", at = @At("HEAD"), cancellable = true)
    private static void townstead$configuredThirst(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        var effect = DataDrivenThirstCompat.tanProjection(stack);
        if (effect.hydrates()) cir.setReturnValue(effect.immediateHydration());
    }
}
