package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.compat.BuildingIconSwap;
import net.conczin.mca.client.gui.BlueprintScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Legacy (pre-floor-system) MCA choke point for building icons. On MCA 7.7.x the
 * atlas blit still lives in {@code BlueprintScreen.drawBuildingIcon}; we swap in a
 * {@code townsteadNodeItem} there when the icon's {@code (u, v)} slot maps to one.
 *
 * <p>The newer floor-system MCA removed this method, so this mixin is applied
 * only when the runtime lacks the new API (see {@code TownsteadMixinPlugin});
 * on new MCA, {@link WidgetUtilsBuildingIconMixin} and
 * {@link BlueprintMapRendererIconMixin} take over instead. The injector targets
 * the method by name (no {@code @Shadow}) so this class still compiles against
 * the newer MCA jar, where {@code drawBuildingIcon} no longer exists.
 */
@Mixin(BlueprintScreen.class)
public abstract class BlueprintScreenLegacyIconMixin {
    @Inject(method = "drawBuildingIcon", remap = false, at = @At("HEAD"), cancellable = true)
    private void townstead$swapBuildingIcon(GuiGraphics context, ResourceLocation texture,
            int x, int y, int u, int v, CallbackInfo ci) {
        if (BuildingIconSwap.render(context, x, y, u, v)) {
            ci.cancel();
        }
    }
}
