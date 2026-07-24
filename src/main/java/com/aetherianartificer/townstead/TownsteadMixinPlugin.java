package com.aetherianartificer.townstead;

import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Applies the building-icon-swap mixins that match the running MCA generation.
 *
 * <p>MCA's floor-system rebuild removed {@code BlueprintScreen.drawBuildingIcon}
 * and moved icon drawing into {@code WidgetUtils}/{@code BlueprintMapRenderer}.
 * Townstead compiles against that newer API but still runs on the older 7.7.x
 * builds, so the three icon mixins are mutually exclusive:
 *
 * <ul>
 *   <li>new API → {@code WidgetUtilsBuildingIconMixin} + {@code BlueprintMapRendererIconMixin}</li>
 *   <li>legacy API → {@code BlueprintScreenLegacyIconMixin}</li>
 * </ul>
 *
 * <p>Gating by presence of {@code BlueprintMapRenderer} (new-only) also keeps the
 * legacy mixin from resolving a method that no longer exists (the crash that
 * motivated this split) and keeps the new mixins from resolving a class that does
 * not exist on old MCA. Detection uses a classpath resource lookup so no MCA
 * class is loaded — and therefore frozen — before its own transformers run.
 */
public class TownsteadMixinPlugin implements IMixinConfigPlugin {
    private static final String NEW_API_MARKER = "net/conczin/mca/client/gui/BlueprintMapRenderer.class";
    private static final String FLOOR_V2_MARKER = "net/conczin/mca/server/world/data/ExternalBuilding.class";

    private Boolean newApi;
    private Boolean floorV2;

    private boolean isFloorV2() {
        if (floorV2 == null) {
            floorV2 = TownsteadMixinPlugin.class.getClassLoader().getResource(FLOOR_V2_MARKER) != null;
        }
        return floorV2;
    }

    private boolean isNewApi() {
        if (newApi == null) {
            newApi = TownsteadMixinPlugin.class.getClassLoader().getResource(NEW_API_MARKER) != null;
        }
        return newApi;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.endsWith("WidgetUtilsBuildingIconMixin")
                || mixinClassName.endsWith("BlueprintMapRendererIconMixin")) {
            return isNewApi();
        }
        if (mixinClassName.endsWith("BlueprintScreenLegacyIconMixin")) {
            return !isNewApi();
        }
        // Legacy MCA only. On the floor-system build the payload bloat is already
        // prevented at the source (BuildingTypeSyntheticBlockMixin stops houses
        // recording their walls) and the decode-cap raise
        // (GetVillageResponseLargePacketMixin) covers any legacy save data, so the
        // slimmer is unnecessary there — and keeping it off that version avoids the
        // wire-rewrite touching the block geometry the new map renderer reads.
        if (mixinClassName.endsWith("GetVillageResponseSlimPayloadMixin")) {
            return !isNewApi();
        }
        // Floor-system v2 removed Building.validateBuilding; synthetics are ExternalBuildings
        // there and never see room validation.
        if (mixinClassName.endsWith("BuildingValidateOpenAirMixin")) {
            return !isFloorV2();
        }
        return true;
    }

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, org.objectweb.asm.tree.ClassNode targetClass, String mixinClassName,
            IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, org.objectweb.asm.tree.ClassNode targetClass, String mixinClassName,
            IMixinInfo mixinInfo) {
    }
}
