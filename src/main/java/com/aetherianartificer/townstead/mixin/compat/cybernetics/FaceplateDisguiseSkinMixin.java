package com.aetherianartificer.townstead.mixin.compat.cybernetics;

import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
//? if neoforge {
import com.aetherianartificer.townstead.compat.cybernetics.FaceplateDisguiseClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.PlayerSkin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//?}

/** A disguised player renders in the skin of the player their alias names. */
@Mixin(AbstractClientPlayer.class)
public abstract class FaceplateDisguiseSkinMixin {

    //? if neoforge {
    @Inject(method = "getSkin", at = @At("HEAD"), cancellable = true)
    private void townstead$shownSkin(CallbackInfoReturnable<PlayerSkin> cir) {
        AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;
        java.util.UUID shown = FaceplateDisguiseClient.shownWhileRendering(self);
        if (shown == null || shown.equals(self.getUUID()) || Minecraft.getInstance().getConnection() == null) return;
        PlayerInfo info = Minecraft.getInstance().getConnection().getPlayerInfo(shown);
        if (info != null) cir.setReturnValue(info.getSkin());
    }
    //?}
}
