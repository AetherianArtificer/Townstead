package com.aetherianartificer.townstead;

import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;
import java.util.Set;

/**
 * Applies the building-icon-swap mixins that match the running MCA generation.
 *
 * <p>MCA moved map rendering in stages. The 1.20.1 build draws footprints in
 * {@code BlueprintScreen}; the later floor-system build introduces
 * {@code BlueprintMapRenderer}. Townstead selects the integration that can see
 * the building type directly, because its item icons are keyed by type rather
 * than MCA atlas coordinates.
 *
 * <ul>
 *   <li>{@code BlueprintMapRenderer} present → {@code BlueprintMapRendererIconMixin}</li>
 *   <li>otherwise → {@code BlueprintScreenLegacyIconMixin}</li>
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
    private static final String EMF_STATE_MARKER =
            "traben/entity_model_features/models/animation/state/EMFState.class";
    private static final String EMOTECRAFT_EMF_MIXIN_MARKER =
            "io/github/kosmx/emotes/arch/mixin/emf/EMFAnimationEntityContextMixin.class";
    private static final String EMF_CONTEXT =
            "traben/entity_model_features/models/animation/EMFAnimationEntityContext";
    private static final String EMF_STATE =
            "traben/entity_model_features/models/animation/state/EMFState";
    private static final String EMF_RENDER_STATE_DESC =
            "Ltraben/entity_model_features/models/animation/state/EMFEntityRenderState;";

    private Boolean newApi;
    private Boolean widgetUtils;
    private Boolean floorV2;
    private Boolean mcaForge;
    private Boolean emfEmotecraftBridge;

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

    private boolean isMcaForge() {
        if (mcaForge == null) {
            ClassLoader loader = TownsteadMixinPlugin.class.getClassLoader();
            mcaForge = loader.getResource(FORGE_LOADER_MARKER) != null
                    && (loader.getResource(MCA_FORGE_PLAYER_MIXIN) != null
                    || loader.getResource(MCA_LEGACY_FORGE_PLAYER_MIXIN) != null);
        }
        return mcaForge;
    }

    private boolean needsEmfEmotecraftBridge() {
        if (emfEmotecraftBridge == null) {
            ClassLoader loader = TownsteadMixinPlugin.class.getClassLoader();
            emfEmotecraftBridge = loader.getResource(EMF_STATE_MARKER) != null
                    && loader.getResource(EMOTECRAFT_EMF_MIXIN_MARKER) != null;
        }
        return emfEmotecraftBridge;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.endsWith("McaPlayerArmOverlayMixin")) {
            return isMcaForge();
        }
        if (mixinClassName.endsWith("EmfAnimationEntityContextCompatMixin")) {
            return needsEmfEmotecraftBridge();
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
        if (mixinClassName.endsWith("EmfAnimationEntityContextCompatMixin")) {
            addEmfStateBridge(targetClass);
        }
    }

    /**
     * EMF 3.3 moved its current render state to {@code EMFState.state()}, while Emotecraft's
     * final 1.21.1 build still calls the former public accessor. Add that binary-compatible
     * forwarding method only when it is absent. EMF 3.2 already owns the method and is unchanged.
     */
    static boolean addEmfStateBridge(org.objectweb.asm.tree.ClassNode targetClass) {
        String descriptor = "()" + EMF_RENDER_STATE_DESC;
        if (!EMF_CONTEXT.equals(targetClass.name)) return false;
        if (targetClass.methods.stream().anyMatch(method ->
                "getEmfState".equals(method.name) && descriptor.equals(method.desc))) {
            return false;
        }
        MethodNode bridge = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "getEmfState", descriptor, null, null);
        bridge.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, EMF_STATE,
                "state", descriptor, false));
        bridge.instructions.add(new InsnNode(Opcodes.ARETURN));
        bridge.maxStack = 1;
        bridge.maxLocals = 0;
        targetClass.methods.add(bridge);
        return true;
    }

    @Override
    public void postApply(String targetClassName, org.objectweb.asm.tree.ClassNode targetClass, String mixinClassName,
            IMixinInfo mixinInfo) {
    }
}
