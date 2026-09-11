package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.hangout.HangoutEngine;
import net.minecraft.world.entity.ai.behavior.ShowTradesToPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Do not advertise trades (flowers, etc.) in the hand during a venue visit. */
@Mixin(ShowTradesToPlayer.class)
public abstract class HangoutTradePreviewMixin {
    @Inject(method = "displayAsHeldItem", at = @At("HEAD"), cancellable = true)
    private static void townstead$skipVisitPreview(Villager villager, ItemStack stack, CallbackInfo ci) {
        if (HangoutEngine.visit(villager.getUUID()) != null) ci.cancel();
    }
}
