package com.aetherianartificer.townstead.mixin.client;

import com.aetherianartificer.townstead.client.gui.inventory.VillagerInventoryScreen;
import net.conczin.mca.client.render.VillagerLikeEntityMCARenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * No nameplate on the villager drawn inside their inventory screen's portrait. MCA already makes this
 * exception for its own editor screen; the inventory screen names the villager in its title instead.
 */
@Mixin(VillagerLikeEntityMCARenderer.class)
public abstract class VillagerPortraitNameTagMixin {

    @Inject(
            //? if >=1.21 {
            method = "shouldShowName(Lnet/minecraft/world/entity/Mob;)Z",
            //?} else {
            /*method = "m_6512_(Lnet/minecraft/world/entity/Mob;)Z",
            *///?}
            at = @At("HEAD"), cancellable = true, remap = false)
    private void townstead$hideNameInPortrait(Mob villager, CallbackInfoReturnable<Boolean> cir) {
        if (Minecraft.getInstance().screen instanceof VillagerInventoryScreen) cir.setReturnValue(false);
    }
}
