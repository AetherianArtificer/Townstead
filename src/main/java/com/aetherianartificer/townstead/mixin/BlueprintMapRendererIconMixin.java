package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.compat.BuildingIconSwap;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * New floor-system MCA choke point for footprint (structural) building icons.
 * These carry a per-layer scale and floating-point map coordinates, so they use
 * the scaled swap variant. Companion to {@link WidgetUtilsBuildingIconMixin},
 * which handles grouped POI icons.
 *
 * <p>{@code BlueprintMapRenderer} is package-private (hence the string target)
 * and does not exist on legacy MCA, so this mixin is applied only when the
 * runtime exposes the new API (see {@code TownsteadMixinPlugin}); the plugin gate
 * keeps the missing target class from ever being resolved on older MCA.
 */
@Mixin(targets = "net.conczin.mca.client.gui.BlueprintMapRenderer")
public class BlueprintMapRendererIconMixin {
    @Inject(method = "drawScaledBuildingIcon", remap = false, at = @At("HEAD"), cancellable = true)
    private static void townstead$swapScaledBuildingIcon(GuiGraphics context, ResourceLocation texture,
            double x, double y, int u, int v, float scale, CallbackInfo ci) {
        if (BuildingIconSwap.renderScaled(context, x, y, u, v, scale)) {
            ci.cancel();
        }
    }
}
