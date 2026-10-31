package com.aetherianartificer.townstead.mixin.client;

import com.aetherianartificer.townstead.client.gui.ScreenInputHooks;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tells a screen mixin its screen was removed, for screens that do not declare removed() themselves. */
@Mixin(Screen.class)
public abstract class ScreenRemovedHookMixin {
    //? if neoforge {
    @Inject(method = "removed", at = @At("TAIL"))
    //?} else {
    /*@Inject(method = "m_7861_", remap = false, at = @At("TAIL"))
    *///?}
    private void townstead$notifyRemoved(CallbackInfo ci) {
        if (this instanceof ScreenInputHooks hooks) hooks.townstead$screenRemoved();
    }
}
