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
    private static final String MCA_PLAYER_MIXIN = "net/conczin/mca/mixin/client/MixinPlayerRenderer.class";
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

    private static final String CAPITALS_BIRTH_MIXIN =
            "com/majesttyx/mcacapitals/mixin/BabyItemBirthIdentityMixin.class";
    private static final String CAPITALS_GUARD_MIXIN =
            "com/majesttyx/mcacapitals/mixin/GuardEnemiesSensorCampaignMixin.class";
    private static final String OLD_GUARD_RANGE_DESC =
            "(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/LivingEntity;)Z";
    private static final String OLD_BIRTH_CHILD_DESC = "(Lnet/minecraft/world/item/ItemStack;"
            + "Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/server/level/ServerPlayer;)"
            + "Lnet/conczin/mca/entity/VillagerEntityMCA;";

    private Boolean newApi;
    private Boolean widgetUtils;
    private Boolean floorV2;
    private Boolean mcaForge;
    private Boolean emfEmotecraftBridge;
    private Boolean mcaArmLayer;

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

    /** MCA builds that draw first-person hands per layer carry {@code mca$renderArmLayer} in this mixin. */
    private boolean hasMcaArmLayer() {
        if (mcaArmLayer == null) {
            mcaArmLayer = false;
            try (java.io.InputStream in = TownsteadMixinPlugin.class.getClassLoader()
                    .getResourceAsStream(MCA_PLAYER_MIXIN)) {
                if (in != null) {
                    String bytes = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.ISO_8859_1);
                    mcaArmLayer = bytes.contains("mca$renderArmLayer");
                }
            } catch (java.io.IOException ignored) {
            }
        }
        return mcaArmLayer;
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
        // MCA Descendants: one mixin over its utilities and event handlers, only when it is installed.
        if (mixinClassName.endsWith("DescendantsMixin")) {
            return TownsteadMixinPlugin.class.getClassLoader()
                    .getResource("net/dannyfather/mca_descendants/util/ModUtils.class") != null;
        }
        // Only MCA builds whose Destiny reads its locations from config have this method; newer
        // builds send the locations, and Townstead adds "Where you died" to that list instead.
        if (mixinClassName.endsWith("DestinyScreenWhereYouDiedMixin")) {
            return TownsteadMixinPlugin.class.getClassLoader().getResource(FORGE_LOADER_MARKER) != null;
        }
        if (mixinClassName.endsWith("McaPlayerArmOverlayMixin")) {
            return isMcaForge();
        }
        if (mixinClassName.endsWith("McaPlayerArmLayerOverlayMixin")) {
            return hasMcaArmLayer();
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
        // Legacy MCA only. On the floor-system build no building type lists generic
        // structural material, so houses do not record their walls, and the decode-cap raise
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
        // Its targets (the enforcing processBuilding and getBlockedResult) are pre-floor-system
        // too: v2 keeps open-air sites out of room scans by storing them apart.
        if (mixinClassName.endsWith("VillageManagerOpenAirRoomMixin")
                || mixinClassName.endsWith("VillageManagerCaveRoomMixin")) {
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
        if (mixinClassName.endsWith("BabyItemInheritanceMixin")) {
            addCapitalsBirthTarget(targetClass);
        }
        if (mixinClassName.endsWith("GuardEnemyLeashMixin")) {
            addCapitalsGuardTarget(targetClass);
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

    /**
     * MCA Capitals injects into {@code birthChild} by name alone, with a handler for its old
     * signature. Against MCA's current signature that is a fatal mixin error, and the game cannot
     * load. A name-only injection takes the first method with that name, so this puts an uncalled
     * method with the old signature first, and Capitals' hook lands there. Townstead runs what the
     * hook does itself, through {@code CapitalsNameBridge.recordBirth}.
     */
    private static void addCapitalsBirthTarget(org.objectweb.asm.tree.ClassNode target) {
        if (TownsteadMixinPlugin.class.getClassLoader().getResource(CAPITALS_BIRTH_MIXIN) == null) return;
        boolean current = false;
        for (MethodNode method : target.methods) {
            if (!"birthChild".equals(method.name)) continue;
            if (OLD_BIRTH_CHILD_DESC.equals(method.desc)) return;
            if (method.desc.contains("Lnet/minecraft/world/phys/Vec3;")) current = true;
        }
        if (!current) return;
        MethodNode stand = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_SYNTHETIC, "birthChild",
                OLD_BIRTH_CHILD_DESC, null, null);
        stand.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
        stand.instructions.add(new InsnNode(Opcodes.ARETURN));
        stand.maxStack = 1;
        stand.maxLocals = 4;
        target.methods.add(0, stand);
    }

    /** The same stand-in for Capitals' guard range hook. {@code CapitalsCampaignBridge} applies the rule. */
    private static void addCapitalsGuardTarget(org.objectweb.asm.tree.ClassNode target) {
        if (TownsteadMixinPlugin.class.getClassLoader().getResource(CAPITALS_GUARD_MIXIN) == null) return;
        boolean current = false;
        for (MethodNode method : target.methods) {
            if (!"isWithinGuardEnemyRange".equals(method.name)) continue;
            if (OLD_GUARD_RANGE_DESC.equals(method.desc)) return;
            current = true;
        }
        if (!current) return;
        MethodNode stand = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_SYNTHETIC, "isWithinGuardEnemyRange",
                OLD_GUARD_RANGE_DESC, null, null);
        stand.instructions.add(new InsnNode(Opcodes.ICONST_0));
        stand.instructions.add(new InsnNode(Opcodes.IRETURN));
        stand.maxStack = 1;
        stand.maxLocals = 3;
        target.methods.add(0, stand);
    }

    @Override
    public void postApply(String targetClassName, org.objectweb.asm.tree.ClassNode targetClass, String mixinClassName,
            IMixinInfo mixinInfo) {
    }
}
