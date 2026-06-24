package com.aetherianartificer.townstead.client.species;

import com.aetherianartificer.townstead.client.origin.OriginCatalogClient;
import com.aetherianartificer.townstead.client.origin.OriginClientStore;
import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.origin.Animations;
import com.aetherianartificer.townstead.origin.Hold;
import com.aetherianartificer.townstead.origin.OriginCatalogEntry;
import com.aetherianartificer.townstead.origin.rig.RigDefinition;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.SpiderModel;
import net.minecraft.client.model.geom.LayerDefinitions;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Client registry mapping a species {@code rig.base} to a vanilla humanoid model + texture, so an
 * alternate-rig villager (e.g. a skeleton) renders that model via {@link SpeciesRigLayer} instead
 * of MCA's villager body layers. First slice: humanoid vanilla models only; non-humanoid rigs
 * (spider, horse) report {@link #isAlternate} false until the layer generalization off
 * {@code HumanoidModel}, so they harmlessly fall back to the villager body.
 */
public final class RigModels {

    private static final String VILLAGER = "mca:villager";
    private static final Map<String, HumanoidModel<LivingEntity>> MODELS = new HashMap<>();
    // The baked root part per rig, kept so held-item anchoring can resolve a bone by its geo name.
    private static final Map<String, ModelPart> ROOTS = new HashMap<>();

    // Non-humanoid vanilla model classes, instantiated from the rig's baked layer root so the model's
    // own setupAnim provides body-plan-correct animation (a spider's 8-leg gait, etc.). Keyed by the
    // rig's modelRef, the vanilla model id the data pack names: the one Java seam a custom body plan
    // needs, since a model class is code. Everything else (which layer, texture, scale) stays data.
    private static final Map<String, Function<ModelPart, EntityModel<LivingEntity>>> GENERIC_FACTORIES =
            Map.of("minecraft:spider", root -> new SpiderModel<>(root));
    private static final Map<String, EntityModel<LivingEntity>> GENERIC_MODELS = new HashMap<>();

    private RigModels() {}

    /**
     * The rig.base for an entity: a life-stage rig override (e.g. an "egg" stage renders an egg model)
     * when the current stage has one, else the species rig, else the villager default. Resolved through
     * the synced origin catalog + the client life store's current stage index.
     */
    public static String rigBaseFor(LivingEntity entity) {
        String originId = OriginClientStore.resolve(entity);
        if (originId == null || originId.isEmpty()) return VILLAGER;
        OriginCatalogEntry origin = OriginCatalogClient.origin(originId);
        if (origin == null) return VILLAGER;
        String stageRig = stageRigFor(entity, origin);
        if (stageRig != null && !stageRig.isEmpty()) return stageRig;
        return origin.rigBase() == null || origin.rigBase().isEmpty() ? VILLAGER : origin.rigBase();
    }

    // Editor preview: the editor's villager is a throwaway client entity with no synced life snapshot, so
    // the age slider pushes the previewed stage index here (keyed by entity id) while it drags, so the rig
    // swaps live (egg <-> spider) as the slider crosses a stage. Mirrors LifeStageScale's scale preview.
    private static final Map<Integer, Integer> PREVIEW_STAGE = new HashMap<>();

    /** Editor: set the previewed life-stage index for an entity id (drives the per-stage rig swap). */
    public static void setPreviewStage(int entityId, int stageIndex) {
        PREVIEW_STAGE.put(entityId, stageIndex);
    }

    public static void clearPreviewStage(int entityId) {
        PREVIEW_STAGE.remove(entityId);
    }

    /** The current life stage's rig override for this entity (editor preview, else per-origin catalog), or null. */
    private static String stageRigFor(LivingEntity entity, OriginCatalogEntry origin) {
        java.util.List<String> rigs = origin.stageRigs();
        if (rigs == null || rigs.isEmpty()) return null;
        int idx;
        Integer preview = PREVIEW_STAGE.get(entity.getId());
        if (preview != null) {
            idx = preview;
        } else {
            com.aetherianartificer.townstead.calendar.LifeClientStore.Snapshot snap =
                    com.aetherianartificer.townstead.calendar.LifeClientStore.get(entity.getId());
            if (snap == null) return null;
            idx = snap.currentStageIndex();
        }
        return idx >= 0 && idx < rigs.size() ? rigs.get(idx) : null;
    }

    /**
     * True when the rig resolves to a renderable alternate definition, so the swap engages. Covers both
     * {@code entity_layer} rigs (vanilla model layers) and {@code geometry} rigs (custom Bedrock
     * {@code .geo.json}, baked + synced via the attachment blob pipeline and rendered through the generic
     * static path). A geometry rig that hasn't materialized yet renders nothing for a frame, like a
     * not-yet-synced texture — acceptable for the sync window.
     */
    public static boolean isAlternate(String rigBase) {
        RigDefinition def = OriginCatalogClient.rig(rigBase);
        return def != null && (def.modelType() == RigDefinition.ModelType.ENTITY_LAYER
                || def.modelType() == RigDefinition.ModelType.GEOMETRY);
    }

    /** The resolved rig definition for a rig id (vanilla aliases applied), or null if unknown. */
    public static RigDefinition definition(String rigBase) {
        return OriginCatalogClient.rig(rigBase);
    }

    /** The cached humanoid model for a rig, baked from its vanilla model layer; null if unsupported. */
    public static HumanoidModel<LivingEntity> model(String rigBase) {
        if (MODELS.containsKey(rigBase)) return MODELS.get(rigBase);
        HumanoidModel<LivingEntity> model = null;
        ModelPart part = bakeRoot(rigBase);
        if (part != null) {
            model = new HumanoidModel<>(part);
            ROOTS.put(rigBase, part);
        }
        MODELS.put(rigBase, model);
        return model;
    }

    /**
     * True when the rig's model is a registered non-humanoid vanilla model, so it renders through the
     * generic path ({@link #genericModel}) instead of the humanoid one. The humanoid path is unchanged
     * for every existing rig; only a rig whose {@code modelRef} has a {@link #GENERIC_FACTORIES} entry
     * (e.g. {@code minecraft:spider}) takes the generic branch.
     */
    public static boolean isGeneric(String rigBase) {
        RigDefinition def = OriginCatalogClient.rig(rigBase);
        if (def == null) return false;
        // A custom-geometry rig is always generic (static body, no humanoid assumptions).
        if (def.modelType() == RigDefinition.ModelType.GEOMETRY) return true;
        return def.modelType() == RigDefinition.ModelType.ENTITY_LAYER
                && GENERIC_FACTORIES.containsKey(def.modelRef());
    }

    /**
     * The cached non-humanoid model for a generic rig: the rig's vanilla layer baked EMF-free, wrapped
     * in its real vanilla model class (so {@code setupAnim} animates the body plan), or null if the rig
     * is not a registered generic model. The baked root is kept in {@link #ROOTS} so a face overlay can
     * still resolve a bone (e.g. the spider's {@code head}) by name.
     */
    public static EntityModel<LivingEntity> genericModel(String rigBase) {
        if (GENERIC_MODELS.containsKey(rigBase)) return GENERIC_MODELS.get(rigBase);
        RigDefinition def = OriginCatalogClient.rig(rigBase);
        if (def == null) return null;
        EntityModel<LivingEntity> model = null;
        if (def.modelType() == RigDefinition.ModelType.ENTITY_LAYER) {
            Function<ModelPart, EntityModel<LivingEntity>> factory = GENERIC_FACTORIES.get(def.modelRef());
            if (factory != null) {
                ModelPart part = bakeLayer(new ModelLayerLocation(DataPackLang.parseId(def.modelRef()), def.modelLayer()));
                if (part != null) {
                    model = factory.apply(part);
                    ROOTS.put(rigBase, part);
                }
            }
            // The vanilla-layer bake is deterministic, so cache the result (even null = unsupported).
            GENERIC_MODELS.put(rigBase, model);
        } else if (def.modelType() == RigDefinition.ModelType.GEOMETRY) {
            // Custom Bedrock model, baked + synced through the attachment blob pipeline (modelRef is the
            // geo's logical id, e.g. "townstead_spider:geo/egg.geo.json"). It may not have materialized
            // yet, so only cache once it's ready — otherwise leave it out so the next frame retries.
            ModelPart part = com.aetherianartificer.townstead.client.attachment.AttachmentClient.namedGeo(def.modelRef());
            if (part != null) {
                model = new StaticRigModel<>(part);
                ROOTS.put(rigBase, part);
                GENERIC_MODELS.put(rigBase, model);
            }
        }
        return model;
    }

    /**
     * Bake the rig's root part from its definition. An {@code entity_layer} rig bakes the named
     * vanilla/mod model layer's {@link LayerDefinition} directly (see {@link #bakeLayer}); a
     * {@code geometry} rig (custom {@code .geo.json}) is a later phase and bakes nothing yet.
     */
    private static ModelPart bakeRoot(String rigBase) {
        RigDefinition def = OriginCatalogClient.rig(rigBase);
        if (def == null || def.modelType() != RigDefinition.ModelType.ENTITY_LAYER) return null;
        return bakeLayer(new ModelLayerLocation(DataPackLang.parseId(def.modelRef()), def.modelLayer()));
    }

    /**
     * Bake a vanilla layer definition's root directly, NOT through {@code EntityModelSet.bakeLayer}:
     * that path is intercepted by Entity Model Features, which returns a CEM/Fresh-Animations part
     * that re-poses its own bones at render and stomps every pose we set. {@code createRoots()} builds
     * every vanilla layer definition once; {@code bakeRoot()} on the body or armor definition stays
     * EMF-free, and the animation bridge drives the bones instead.
     */
    private static ModelPart bakeLayer(ModelLayerLocation loc) {
        if (loc == null) return null;
        if (layerDefs == null) layerDefs = LayerDefinitions.createRoots();
        LayerDefinition def = layerDefs.get(loc);
        return def == null ? null : def.bakeRoot();
    }

    /** Bake a layer from an {@code "ns:path#layer"} reference (default layer {@code main}). */
    private static ModelPart bakeLayerRef(String ref) {
        if (ref == null || ref.isEmpty()) return null;
        int hash = ref.indexOf('#');
        String id = hash >= 0 ? ref.substring(0, hash) : ref;
        String layer = hash >= 0 ? ref.substring(hash + 1) : "main";
        return bakeLayer(new ModelLayerLocation(DataPackLang.parseId(id), layer));
    }

    /** The baked root part for a rig (baking it if needed), so the bridge can resolve bones by name. */
    public static ModelPart root(String rigBase) {
        ModelPart root = ROOTS.get(rigBase);
        if (root == null) {
            model(rigBase);
            root = ROOTS.get(rigBase);
        }
        return root;
    }

    /**
     * Resolve a rig bone by its geo name (e.g. {@code "right_arm"}), so a held item can be anchored
     * to it. Vanilla humanoid bones are direct children of the baked root, so a one-level lookup
     * covers the current rigs; nested custom-geo bones will need a name index built at bake time.
     */
    public static ModelPart bone(String rigBase, String name) {
        if (name == null || name.isEmpty()) return null;
        ModelPart root = ROOTS.get(rigBase);
        if (root == null) {
            model(rigBase);
            root = ROOTS.get(rigBase);
        }
        return root != null && root.hasChild(name) ? root.getChild(name) : null;
    }

    /**
     * Resolve a rig bone the right way for either body plan: a generic (non-humanoid) rig is baked via
     * {@link #genericModel} first (its humanoid bake would throw on the missing arm/leg bones), a
     * humanoid rig through {@link #bone}. Returns null if the bone is absent or the model isn't baked yet.
     */
    public static ModelPart cameraBone(String rigBase, String name) {
        if (name == null || name.isEmpty()) return null;
        if (isGeneric(rigBase)) {
            genericModel(rigBase);
            return bakedBone(rigBase, name);
        }
        return bone(rigBase, name);
    }

    /**
     * A baked rig bone by geo name from the already-cached root, WITHOUT triggering a humanoid bake.
     * Safe for generic (non-humanoid) rigs, whose root must be baked via {@link #genericModel} first;
     * returns null if the root is not baked yet or the bone is absent.
     */
    public static ModelPart bakedBone(String rigBase, String name) {
        if (name == null || name.isEmpty()) return null;
        ModelPart root = ROOTS.get(rigBase);
        return root != null && root.hasChild(name) ? root.getChild(name) : null;
    }

    /**
     * Host-renderer "equivalence" baselines: how much to scale a vanilla humanoid rig so it renders
     * at the same height as the host it replaces, so an authored {@code rig.scale} of 1.0 means
     * host-normal. Empirically both the villager renderer and the genetics-player renderer draw the
     * swapped humanoid rig at about the right height with no extra scale, so both are 1.0; the
     * baseline stays a per-host constant (passed to {@link SpeciesRigLayer} by each host's mixin) as
     * a tuning hook in case a future host or non-humanoid rig needs its own correction. Authored
     * {@code rig.scale} multiplies on top.
     */
    public static final float VILLAGER_HOST_BASELINE = 1.0f;
    public static final float PLAYER_HOST_BASELINE = 1.0f;

    /** The species' authored uniform render scale for this entity (from the data pack; 1.0 default). */
    public static float scaleFor(LivingEntity entity) {
        String originId = OriginClientStore.resolve(entity);
        if (originId == null || originId.isEmpty()) return 1.0f;
        OriginCatalogEntry origin = OriginCatalogClient.origin(originId);
        return origin == null || origin.rigScale() <= 0f ? 1.0f : origin.rigScale();
    }

    /** Whether this entity's species shows breasts (true unless a species opts out). */
    public static boolean breasts(LivingEntity entity) {
        String originId = OriginClientStore.resolve(entity);
        if (originId == null || originId.isEmpty()) return true;
        OriginCatalogEntry origin = OriginCatalogClient.origin(originId);
        return origin == null || origin.breasts();
    }

    /** The species' per-state animation sources for this entity (humanoid default; never null). */
    public static Animations animations(LivingEntity entity) {
        String originId = OriginClientStore.resolve(entity);
        if (originId == null || originId.isEmpty()) return Animations.DEFAULT;
        OriginCatalogEntry origin = OriginCatalogClient.origin(originId);
        return origin == null || origin.animations() == null ? Animations.DEFAULT : origin.animations();
    }

    /**
     * The rig's authored grip for the main or off hand, or null when that hand cannot hold (so its item
     * should not render). Null also when the entity has no synced rig.
     */
    public static Hold.Grip holdGrip(LivingEntity entity, boolean offHand) {
        RigDefinition def = OriginCatalogClient.rig(rigBaseFor(entity));
        if (def == null || def.hold() == null) return null;
        return offHand ? def.hold().offhand() : def.hold().mainhand();
    }

    public static ResourceLocation texture(String rigBase) {
        RigDefinition def = OriginCatalogClient.rig(rigBase);
        if (def == null || def.texture() == null || def.texture().isEmpty()) return null;
        // Prefer a datapack-synced texture (no resource pack needed); fall back to a plain resource
        // location for vanilla / resource-pack textures (e.g. minecraft:textures/entity/skeleton).
        ResourceLocation synced =
                com.aetherianartificer.townstead.client.attachment.AttachmentClient.namedTexture(def.texture());
        return synced != null ? synced : DataPackLang.parseId(def.texture());
    }

    // All vanilla layer definitions, built once. Used to bake body and armor models by bakeRoot()
    // directly, which bypasses the EMF-intercepted EntityModelSet.bakeLayer.
    private static Map<ModelLayerLocation, LayerDefinition> layerDefs;

    /**
     * Bake the rig's armor model part (inner = leggings/boots, outer = helmet/chest/arms) from the
     * definition's armor layers, so worn armor takes the rig's proportions (e.g. a skeleton's thin
     * arms and legs) instead of the wide humanoid default. Null when the rig declares no armor layers,
     * leaving the caller to fall back to a generic humanoid armor mesh.
     */
    public static ModelPart bakeArmorPart(String rigBase, boolean inner) {
        RigDefinition def = OriginCatalogClient.rig(rigBase);
        if (def == null || def.armorType() != RigDefinition.ArmorType.LAYERS) return null;
        return bakeLayerRef(inner ? def.armorInner() : def.armorOuter());
    }
}
