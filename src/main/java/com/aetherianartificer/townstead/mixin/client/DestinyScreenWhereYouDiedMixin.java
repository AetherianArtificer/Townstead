package com.aetherianartificer.townstead.mixin.client;

import com.aetherianartificer.townstead.client.rebirth.RebirthDestinyClient;
import com.aetherianartificer.townstead.rebirth.Rebirth;
import net.conczin.mca.client.gui.DestinyScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Puts "Where you died" first in Destiny's locations after a rebirth, on MCA builds that read those
 * locations from config. The mixin plugin applies it only there.
 */
@Mixin(value = DestinyScreen.class, remap = false)
public abstract class DestinyScreenWhereYouDiedMixin {
    @Inject(method = "getDestinyLocations", at = @At("RETURN"), cancellable = true)
    private void townstead$addWhereYouDied(CallbackInfoReturnable<List<String>> cir) {
        if (!RebirthDestinyClient.offered()) return;
        List<String> locations = new ArrayList<>();
        locations.add(Rebirth.DESTINY_LOCATION);
        locations.addAll(cir.getReturnValue());
        cir.setReturnValue(locations);
    }
}
