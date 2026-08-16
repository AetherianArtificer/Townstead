package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.compat.BuildingIconSwap;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.conczin.mca.MCA;
import net.conczin.mca.resources.data.BuildingType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
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
    @Unique
    private static final float TOWNSTEAD$FOOTPRINT_ICON_MIN_SCALE = 0.90f;
    @Unique
    private static final float TOWNSTEAD$FOOTPRINT_ICON_MAX_SCALE = 1.35f;
    @Unique
    private static final float TOWNSTEAD$FOOTPRINT_ICON_AREA_REFERENCE = 6.0f;

    @Unique
    private static final ResourceLocation townstead$buildingIcons = MCA.locate("textures/buildings.png");

    @Unique
    private static final ThreadLocal<BuildingType> townstead$externalFootprintIcon = new ThreadLocal<>();

    /** MCA's own screen-size-correct icon path used by ordinary room footprints. */
    @Shadow(remap = false)
    private static void drawScaledBuildingIcon(GuiGraphics context, ResourceLocation texture,
            double x, double y, int u, int v, float scale) {
        throw new AssertionError();
    }

    @Inject(method = "drawScaledBuildingIcon", remap = false, at = @At("HEAD"), cancellable = true)
    private static void townstead$swapScaledBuildingIcon(GuiGraphics context, ResourceLocation texture,
            double x, double y, int u, int v, float scale, CallbackInfo ci) {
        if (BuildingIconSwap.renderScaled(context, x, y, u, v, scale)) {
            ci.cancel();
        }
    }

    /**
     * MCA's floor-system renderer treats {@code BuildingType.icon} as an either/or switch for
     * external buildings: {@code true} draws a point icon and discards the footprint, while
     * {@code false} draws the footprint and never considers {@code hasIcon()}. Rooms do not have
     * that limitation. Remember footprint-mode external types that still advertise an icon so the
     * following render hook can overlay it without changing their geometry mode.
     */
    @WrapOperation(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/conczin/mca/resources/data/BuildingType;isIcon()Z"
            ),
            require = 1,
            remap = false
    )
    private static boolean townstead$captureExternalFootprintIcon(
            BuildingType type, Operation<Boolean> original) {
        boolean pointIcon = original.call(type);
        townstead$externalFootprintIcon.remove();
        if (!pointIcon && type.visible() && type.hasIcon()) {
            townstead$externalFootprintIcon.set(type);
        }
        return pointIcon;
    }

    @WrapOperation(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/conczin/mca/client/gui/BlueprintMapRenderer;renderRoomRegion(Lnet/minecraft/client/gui/GuiGraphics;IIIIIZZ)V"
            ),
            require = 1,
            remap = false
    )
    private static void townstead$renderExternalFootprintIcon(
            GuiGraphics context,
            int minX,
            int minZ,
            int maxX,
            int maxZ,
            int color,
            boolean floorSelected,
            boolean hovered,
            Operation<Void> original,
            @Local(argsOnly = true, ordinal = 1) boolean showBuildingIcons) {
        original.call(context, minX, minZ, maxX, maxZ, color, floorSelected, hovered);

        BuildingType type = townstead$externalFootprintIcon.get();
        townstead$externalFootprintIcon.remove();
        if (!showBuildingIcons || type == null) return;

        // This hook runs inside BlueprintMapRenderer's world transform. Calling
        // WidgetUtils.drawBuildingIcon here scales the 12 px item by the map zoom,
        // which is the giant-icon bug. Ordinary room icons instead call
        // drawScaledBuildingIcon with iconScale / viewport.scale(). Read the active
        // world scale from the pose and do exactly the same cancellation here.
        float worldScale = Math.abs(context.pose().last().pose().m00());
        if (!Float.isFinite(worldScale) || worldScale <= 0.0001f) return;

        int width = Math.max(1, maxX - minX + 1);
        int depth = Math.max(1, maxZ - minZ + 1);
        float footprintScale = (float) Math.sqrt((double) width * depth)
                / TOWNSTEAD$FOOTPRINT_ICON_AREA_REFERENCE;
        footprintScale = Math.max(TOWNSTEAD$FOOTPRINT_ICON_MIN_SCALE,
                Math.min(TOWNSTEAD$FOOTPRINT_ICON_MAX_SCALE, footprintScale));

        double centerX = (minX + maxX + 1) * 0.5D;
        double centerZ = (minZ + maxZ + 1) * 0.5D;
        drawScaledBuildingIcon(context, townstead$buildingIcons,
                centerX, centerZ, type.iconU(), type.iconV(), footprintScale / worldScale);
    }
}
