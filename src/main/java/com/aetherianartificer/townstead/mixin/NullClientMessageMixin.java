package com.aetherianartificer.townstead.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A sleep refused as OTHER_PROBLEM has no message, and some modded beds (Vampirism's coffin and
 * tent) show it anyway. A null message cannot be encoded and disconnects the player, so it is dropped.
 */
@Mixin(ServerPlayer.class)
public abstract class NullClientMessageMixin {

    //? if neoforge {
    @Inject(method = "displayClientMessage", at = @At("HEAD"), cancellable = true)
    //?} else {
    /*@Inject(method = "m_5661_", at = @At("HEAD"), cancellable = true, remap = false)
    *///?}
    private void townstead$dropNullMessage(Component message, boolean actionBar, CallbackInfo ci) {
        if (message == null) ci.cancel();
    }
}
