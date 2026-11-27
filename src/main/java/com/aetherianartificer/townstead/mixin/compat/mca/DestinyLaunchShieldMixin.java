package com.aetherianartificer.townstead.mixin.compat.mca;

import com.aetherianartificer.townstead.rebirth.DestinyShield;
import net.conczin.mca.server.ServerInteractionManager;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Every Destiny MCA opens, on first join or by command, shields the player until it closes. */
@Mixin(value = ServerInteractionManager.class, remap = false)
public abstract class DestinyLaunchShieldMixin {
    @Inject(method = "launchDestiny", at = @At("HEAD"), require = 0)
    private static void townstead$shield(ServerPlayer player, CallbackInfo ci) {
        DestinyShield.opened(player);
    }
}
