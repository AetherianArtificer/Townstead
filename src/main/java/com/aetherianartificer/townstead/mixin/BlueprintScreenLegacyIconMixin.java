package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.compat.BuildingIconSwap;
import com.aetherianartificer.townstead.compat.BuildingIconResolver;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.conczin.mca.resources.data.BuildingType;
import net.conczin.mca.client.gui.BlueprintScreen;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Legacy (pre-floor-system) MCA map integration. Townstead building definitions
 * stay in footprint mode; this overlays their sidecar item at the footprint's
 * center without reserving fake MCA atlas coordinates.
 *
 * <p>The newer floor-system MCA moves this work into
 * {@link BlueprintMapRendererIconMixin}. The plugin applies exactly one of the
 * two integrations for the running MCA generation.
 */
@Mixin(BlueprintScreen.class)
public abstract class BlueprintScreenLegacyIconMixin {
    @Unique
    private static final ThreadLocal<BuildingType> townstead$footprintIcon = new ThreadLocal<>();

    @WrapOperation(
            method = "renderMap",
            at = @At(value = "INVOKE",
                    target = "Lnet/conczin/mca/resources/data/BuildingType;isIcon()Z"),
            require = 1,
            remap = false
    )
    private boolean townstead$captureFootprintIcon(BuildingType type, Operation<Boolean> original) {
        townstead$footprintIcon.remove();
        if (type.visible() && BuildingIconResolver.nodeItemForType(type.name()).isPresent()) {
            townstead$footprintIcon.set(type);
            return false;
        }
        return original.call(type);
    }

    @WrapOperation(
            method = "renderMap",
            at = @At(value = "INVOKE",
                    target = "Lnet/conczin/mca/client/gui/widget/WidgetUtils;drawRectangle(Lnet/minecraft/client/gui/GuiGraphics;IIIII)V",
                    ordinal = 1),
            require = 1,
            remap = false
    )
    private void townstead$drawFootprintAndItem(GuiGraphics context,
            int minX, int minZ, int maxX, int maxZ, int color, Operation<Void> original) {
        original.call(context, minX, minZ, maxX, maxZ, color);
        BuildingType type = townstead$footprintIcon.get();
        townstead$footprintIcon.remove();
        if (type == null) return;
        BuildingIconSwap.render(context, (minX + maxX) / 2, (minZ + maxZ) / 2,
                type.name());
    }
}
