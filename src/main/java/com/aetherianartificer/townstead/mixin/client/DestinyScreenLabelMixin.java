package com.aetherianartificer.townstead.mixin.client;

import com.aetherianartificer.townstead.client.DestinyLabels;
import net.conczin.mca.client.gui.DestinyScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Names a culture village on the Destiny screen from its culture data (see {@code CultureVillages.labels}).
 * MCA's own key drops the namespace, so every culture's {@code village/town} would share one name.
 */
@Mixin(value = DestinyScreen.class, remap = false)
public abstract class DestinyScreenLabelMixin {
    @Inject(method = "getLocationName", at = @At("RETURN"), cancellable = true, require = 0)
    private void townstead$cultureVillageName(String location, CallbackInfoReturnable<MutableComponent> cir) {
        String label = DestinyLabels.get(location);
        if (label != null) cir.setReturnValue(Component.literal(label));
    }
}
