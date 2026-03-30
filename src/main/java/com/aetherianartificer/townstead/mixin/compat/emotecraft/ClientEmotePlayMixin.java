package com.aetherianartificer.townstead.mixin.compat.emotecraft;

import com.aetherianartificer.townstead.emote.EmotecraftClientRelay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "io.github.kosmx.emotes.main.network.ClientEmotePlay", remap = false)
public abstract class ClientEmotePlayMixin {
    @Inject(
            method = "clientStartLocalEmote(Ldev/kosmx/playerAnim/core/data/KeyframeAnimation;I)Z",
            at = @At("TAIL"),
            remap = false
    )
    private static void townstead$relayEmotecraftStart(CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.TRUE.equals(cir.getReturnValue())) {
            EmotecraftClientRelay.relayCurrentLocalAnimation();
        }
    }
}
