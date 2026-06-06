package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
//? if >=1.21 {
import net.minecraft.network.protocol.common.ServerboundClientInformationPacket;
//?} else {
/*import net.minecraft.network.protocol.game.ServerboundClientInformationPacket;
*///?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Minecraft sends updated client information when a connected player changes
 * language. Resend the calendar snapshot after vanilla stores that information
 * so data-pack locale fallbacks switch immediately.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerLocaleChangeMixin {

    //? if >=1.21 {
    @Inject(method = "handleClientInformation", at = @At("TAIL"))
    //?} else {
    /*@Inject(method = "m_5617_", remap = false, at = @At("TAIL"))
    *///?}
    private void townstead$resyncCalendarLocale(
            ServerboundClientInformationPacket packet, CallbackInfo ci) {
        ServerGamePacketListenerImpl self = (ServerGamePacketListenerImpl) (Object) this;
        Townstead.townstead$sendCalendarSync(self.player);
    }
}
