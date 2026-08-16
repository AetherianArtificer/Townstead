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
 * MCA building-icon choke point used by both the 1.20.1 hybrid backport and the
 * newer floor-system builds. MCA delegates icon drawing to this static helper;
 * we swap in a Townstead node item when the icon's
 * {@code (u, v)} slot maps to one.
 *
 * <p>Applied only when the runtime MCA exposes {@code WidgetUtils}. See
 * {@code TownsteadMixinPlugin} and the legacy counterpart
 * {@code BlueprintScreenLegacyIconMixin}.
 */
@Mixin(WidgetUtils.class)
public class WidgetUtilsBuildingIconMixin {
    @Inject(method = "drawBuildingIcon", remap = false, at = @At("HEAD"), cancellable = true)
    private static void townstead$swapBuildingIcon(GuiGraphics context, ResourceLocation texture,
            int x, int y, int u, int v, CallbackInfo ci) {
        if (BuildingIconSwap.render(context, x, y, u, v)) {
            ci.cancel();
        }
    }
}
