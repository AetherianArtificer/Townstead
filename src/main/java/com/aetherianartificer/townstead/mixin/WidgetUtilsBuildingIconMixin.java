package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.compat.BuildingIconSwap;
import net.conczin.mca.client.gui.widget.WidgetUtils;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Uses MCA's own correctly transformed legacy icon draw point, replacing only Townstead-known
 * building sprites with the item declared by the building type's extended sidecar.
 */
@Mixin(WidgetUtils.class)
public class WidgetUtilsBuildingIconMixin {
    @Inject(method = "drawBuildingIcon", remap = false, at = @At("HEAD"), cancellable = true)
    private static void townstead$swapBuildingIcon(GuiGraphics context, ResourceLocation texture,
            int x, int y, int u, int v, CallbackInfo ci) {
        if (BuildingIconSwap.render(context, x, y, u, v)) ci.cancel();
    }
}
