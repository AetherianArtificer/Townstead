package com.aetherianartificer.townstead.mixin.compat.mca;

import com.aetherianartificer.townstead.rebirth.Rebirth;
import net.conczin.mca.network.c2s.DestinyMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sends a reborn player who picks "Where you died" in Destiny back to where the last life ended. */
@Mixin(value = DestinyMessage.class, remap = false)
public abstract class DestinyMessageRebirthMixin {
    //? if >=1.21 {
    @Inject(method = "handle", at = @At("HEAD"), cancellable = true, require = 0)
    private void townstead$whereYouDied(net.minecraft.world.entity.player.Player player, CallbackInfo ci) {
        var destination = ((DestinyMessage) (Object) this).destination();
        if (destination.isEmpty() || !Rebirth.DESTINY_LOCATION.equals(destination.get().location())) return;
        if (player instanceof net.minecraft.server.level.ServerPlayer sp) Rebirth.chooseWhereYouDied(sp);
        ci.cancel();
    }
    //?} else {
    /*@Inject(method = "receive", at = @At("HEAD"), cancellable = true, require = 0)
    private void townstead$whereYouDied(net.minecraft.server.level.ServerPlayer player, CallbackInfo ci) {
        // Read by reflection, not @Shadow: a missing field would fail the whole mixin at launch.
        String location;
        try {
            java.lang.reflect.Field field = DestinyMessage.class.getDeclaredField("location");
            field.setAccessible(true);
            location = (String) field.get(this);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return;
        }
        if (!Rebirth.DESTINY_LOCATION.equals(location)) return;
        Rebirth.chooseWhereYouDied(player);
        ci.cancel();
    }
    *///?}
}
