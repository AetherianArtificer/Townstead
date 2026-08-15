package com.aetherianartificer.townstead;

import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Applies the building-icon-swap mixins that match the running MCA generation.
 *
 * <p>MCA moved icon drawing in stages. The 1.20.1 backport is a hybrid: it still
 * has {@code BlueprintScreen.drawBuildingIcon}, but that method already delegates
 * to {@code WidgetUtils}. The later floor-system build additionally introduces
 * {@code BlueprintMapRenderer}. Each choke point therefore needs its own marker.
 *
 * <ul>
 *   <li>{@code WidgetUtils} present → {@code WidgetUtilsBuildingIconMixin}</li>
 *   <li>{@code BlueprintMapRenderer} present → {@code BlueprintMapRendererIconMixin}</li>
 *   <li>no {@code WidgetUtils} → {@code BlueprintScreenLegacyIconMixin}</li>
 * </ul>
 *
 * <p>Detection uses classpath resource lookups so no MCA
 * class is loaded — and therefore frozen — before its own transformers run.
 */
public class TownsteadMixinPlugin implements IMixinConfigPlugin {
    private static final String NEW_API_MARKER = "net/conczin/mca/client/gui/BlueprintMapRenderer.class";
    private static final String WIDGET_UTILS_MARKER = "net/conczin/mca/client/gui/widget/WidgetUtils.class";
    private static final String FLOOR_V2_MARKER = "net/conczin/mca/server/world/data/ExternalBuilding.class";
    private static final String FORGE_LOADER_MARKER = "net/minecraftforge/fml/ModList.class";
    private static final String MCA_FORGE_PLAYER_MIXIN =
            "net/conczin/mca/mixin/client/MixinPlayerEntityRenderer.class";
    private static final String MCA_LEGACY_FORGE_PLAYER_MIXIN =
            "forge/net/mca/mixin/client/MixinPlayerEntityRenderer.class";

    private Boolean newApi;
    private Boolean widgetUtils;
    private Boolean floorV2;
    private Boolean forgeLoader;
    private Boolean mcaForge;

    private boolean isNewApi() {
        if (newApi == null) {
            newApi = TownsteadMixinPlugin.class.getClassLoader().getResource(NEW_API_MARKER) != null;
        }
        return newApi;
    }

    private boolean hasWidgetUtils() {
        if (widgetUtils == null) {
            widgetUtils = TownsteadMixinPlugin.class.getClassLoader().getResource(WIDGET_UTILS_MARKER) != null;
        }
        return widgetUtils;
    }

    private boolean isFloorV2() {
        if (floorV2 == null) {
            floorV2 = TownsteadMixinPlugin.class.getClassLoader().getResource(FLOOR_V2_MARKER) != null;
        }
        return floorV2;
    }

    private boolean isForgeLoader() {
        if (forgeLoader == null) {
            forgeLoader = TownsteadMixinPlugin.class.getClassLoader().getResource(FORGE_LOADER_MARKER) != null;
        }
        return forgeLoader;
    }

    private boolean isMcaForge() {
        if (mcaForge == null) {
            ClassLoader loader = TownsteadMixinPlugin.class.getClassLoader();
            mcaForge = loader.getResource(FORGE_LOADER_MARKER) != null
                    && (loader.getResource(MCA_FORGE_PLAYER_MIXIN) != null
                    || loader.getResource(MCA_LEGACY_FORGE_PLAYER_MIXIN) != null);
        }
        return mcaForge;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.endsWith("McaPlayerArmOverlayMixin")) {
            return isMcaForge();
        }
        // MCA 1.20.1 uses vanilla ImageButton for catalog entries, whose fields
        // are SRG-named at runtime. Keep this catalog-only enhancement off Forge;
        // map icons are handled independently by WidgetUtilsBuildingIconMixin.
        if (mixinClassName.endsWith("LegacyImageButtonMixin")) {
            return !isForgeLoader();
        }
        if (mixinClassName.endsWith("WidgetUtilsBuildingIconMixin")) {
            return hasWidgetUtils();
        }
        if (mixinClassName.endsWith("BlueprintMapRendererIconMixin")) {
            return isNewApi();
        }
        if (mixinClassName.endsWith("BlueprintScreenLegacyIconMixin")) {
            return !hasWidgetUtils();
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
        // Floor-system v2 deleted Building.validateBuilding (and validateBlocks
        // with it), so this injector's target is gone and the config's
        // required=true would turn that into a startup crash. On v2+ the
        // synthetics are ExternalBuildings, which never take the flood-fill
        // validation path this mixin exists to short-circuit. The marker class
        // is present exactly when validateBuilding is absent.
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
