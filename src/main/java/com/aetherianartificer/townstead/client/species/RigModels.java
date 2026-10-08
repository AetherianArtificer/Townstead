package com.aetherianartificer.townstead.client.species;

import com.aetherianartificer.townstead.client.root.RootCatalogClient;
import com.aetherianartificer.townstead.client.root.RootClientStore;
import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.root.Animations;
import com.aetherianartificer.townstead.root.Hold;
import com.aetherianartificer.townstead.root.RootCatalogEntry;
import com.aetherianartificer.townstead.root.rig.RigDefinition;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.SpiderModel;
import net.minecraft.client.model.geom.LayerDefinitions;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.client.attachment.geo.BedrockGeometryLoader;
import com.aetherianartificer.townstead.root.outfit.RootOutfits;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    // Outfit variants of a geometry rig (base bones plus grafted outfit bones), keyed "rigBase|outfit ids".
    private static final Map<String, EntityModel<LivingEntity>> VARIANTS = new HashMap<>();
    private static final int MAX_VARIANTS = 64;
    // Bone name -> parent name per baked geometry root, from its JSON. A vanilla layer has no entry: every
    // bone it names is a direct child of the root.
    private static final Map<ModelPart, Map<String, String>> PARENTS = new IdentityHashMap<>();
    // Resolved bone paths per baked root: name -> parts from the root's child down to the bone.
    private static final Map<ModelPart, Map<String, ModelPart[]>> BONE_PATHS = new IdentityHashMap<>();
    // The root being drawn for a rig while its layer renders (an outfit variant), so bone lookups during
    // that draw land on the parts actually posed and drawn.
    private static final Map<String, ModelPart> ACTIVE = new HashMap<>();
    // Model classes whose setupAnim threw with a foreign entity; drawn at rest from then on.
    private static final Set<EntityModel<?>> STATIC_ONLY = java.util.Collections.newSetFromMap(new IdentityHashMap<>());

    private RigModels() {}

    /** Drop every baked rig model (rig defs or client assets changed); they re-bake on next use. */
    public static void invalidate() {
        MODELS.clear();
        ROOTS.clear();
        GENERIC_MODELS.clear();
        VARIANTS.clear();
        PARENTS.clear();
        BONE_PATHS.clear();
        ACTIVE.clear();
        STATIC_ONLY.clear();
        RigCamera.invalidate();
        RigClips.clear();
        RigGeometryArmor.clear();
        RigArmorRenderer.clear();
    }

    /**
     * Whether the entity is embodied as its species (renders its rig). Delegates to the canonical client
     * gene-expression gate ({@link RootClientStore#expresses}) so the rig render and gene expression never
     * disagree: a player only embodies its species in MCA's full-genetics "Villager" model mode; the
     * "Player"/"Vanilla" modes draw the plain player and treat the species as inheritance data only.
     */
    public static boolean embodied(LivingEntity entity) {
        return RootClientStore.expresses(entity);
    }

    /**
     * The rig.base for an entity: a life-stage rig override (e.g. an "egg" stage renders an egg model)
     * when the current stage has one, else the species rig, else the villager default. Resolved through
     * the synced origin catalog + the client life store's current stage index.
     */
    public static String rigBaseFor(LivingEntity entity) {
        // A player rendered with MCA's vanilla "Player" model is not embodied as its species, so the rig
        // is inheritance data only (see embodied): fall back to the default, reverting every downstream
        // consumer (rig layer, first-person arm, suppress mixins, eye height, hitbox).
        if (!embodied(entity)) return VILLAGER;
        // A state's form (a werewolf's beast) comes before the Root's own body.
        String form = com.aetherianartificer.townstead.client.state.StateFormClient.rig(entity.getId());
        if (form != null && RootCatalogClient.rig(form) != null) return form;
        String rootId = RootClientStore.resolve(entity);
        if (rootId == null || rootId.isEmpty()) return VILLAGER;
        RootCatalogEntry origin = RootCatalogClient.origin(rootId);
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
    private static String stageRigFor(LivingEntity entity, RootCatalogEntry origin) {
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
     * {@code .geo.json} or Blockbench {@code .bbmodel}, baked + synced via the attachment blob pipeline
     * and rendered through the generic static path). A geometry rig that hasn't materialized yet renders nothing for a frame, like a
     * not-yet-synced texture — acceptable for the sync window.
     */
    public static boolean isAlternate(String rigBase) {
        RigDefinition def = RootCatalogClient.rig(rigBase);
        return def != null && (def.modelType() == RigDefinition.ModelType.ENTITY_LAYER
                || def.modelType() == RigDefinition.ModelType.GEOMETRY);
    }

    /** The resolved rig definition for a rig id (vanilla aliases applied), or null if unknown. */
    public static RigDefinition definition(String rigBase) {
        return RootCatalogClient.rig(rigBase);
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
        RigDefinition def = RootCatalogClient.rig(rigBase);
        if (def == null) return false;
        // A custom-geometry rig is always generic (static body, no humanoid assumptions).
        if (def.modelType() == RigDefinition.ModelType.GEOMETRY) return true;
        return def.modelType() == RigDefinition.ModelType.ENTITY_LAYER
                && (GENERIC_FACTORIES.containsKey(def.modelRef())
                    || (def.modelClass() != null && !def.modelClass().isEmpty()));
    }

    /**
     * The cached non-humanoid model for a generic rig: the rig's vanilla layer baked EMF-free, wrapped
     * in its real vanilla model class (so {@code setupAnim} animates the body plan), or null if the rig
     * is not a registered generic model. The baked root is kept in {@link #ROOTS} so a face overlay can
     * still resolve a bone (e.g. the spider's {@code head}) by name.
     */
    public static EntityModel<LivingEntity> genericModel(String rigBase) {
        if (GENERIC_MODELS.containsKey(rigBase)) return GENERIC_MODELS.get(rigBase);
        RigDefinition def = RootCatalogClient.rig(rigBase);
        if (def == null) return null;
        EntityModel<LivingEntity> model = null;
        if (def.modelType() == RigDefinition.ModelType.ENTITY_LAYER) {
            Function<ModelPart, EntityModel<LivingEntity>> factory = GENERIC_FACTORIES.get(def.modelRef());
            if (factory == null && def.modelClass() != null && !def.modelClass().isEmpty()) {
                String className = def.modelClass();
                factory = root -> reflectModel(className, root);
            }
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
            // A Bedrock model by logical id: a pack file synced over the blob pipeline, or another mod's
            // own asset (see RigAssets). A pack blob may not have arrived yet, so only cache once baked;
            // otherwise the next frame retries.
            com.google.gson.JsonObject json = RigAssets.geometry(def.modelRef());
            ModelPart part = json == null ? null : bakeGeometry(json);
            if (part != null) {
                model = new StaticRigModel<>(part);
                ROOTS.put(rigBase, part);
                GENERIC_MODELS.put(rigBase, model);
            }
        }
        return model;
    }

    /**
     * The generic model to draw for this entity: for a geometry rig, the variant with the outfits the
     * entity wears right now grafted on; otherwise (and while an outfit's file is still in flight) the
     * shared base model.
     */
    public static EntityModel<LivingEntity> genericModel(String rigBase, LivingEntity entity) {
        EntityModel<LivingEntity> base = genericModel(rigBase);
        if (base == null) return null;
        RigDefinition def = RootCatalogClient.rig(rigBase);
        if (def == null || def.modelType() != RigDefinition.ModelType.GEOMETRY) return base;
        List<RootOutfits.Outfit> worn = RigOutfitState.worn(entity);
        if (worn.isEmpty()) return base;
        StringBuilder key = new StringBuilder(rigBase);
        for (RootOutfits.Outfit outfit : worn) key.append('|').append(outfit.id());
        EntityModel<LivingEntity> cached = VARIANTS.get(key.toString());
        if (cached != null) return cached;
        JsonObject grafted = graft(RigAssets.geometry(def.modelRef()), worn);
        if (grafted == null) return base;
        ModelPart part = bakeGeometry(grafted);
        if (part == null) return base;
        if (VARIANTS.size() >= MAX_VARIANTS) VARIANTS.clear();
        EntityModel<LivingEntity> variant = new StaticRigModel<>(part);
        VARIANTS.put(key.toString(), variant);
        return variant;
    }

    /**
     * The base geometry with each outfit piece's bones (and their descendants) appended, keeping their
     * authored parents. A bone the body already has is left as is. Null while a pack piece is in flight;
     * a piece nothing provides is skipped.
     */
    private static JsonObject graft(JsonObject base, List<RootOutfits.Outfit> worn) {
        if (base == null) return null;
        JsonObject merged = base.deepCopy();
        JsonArray bones = bonesOf(merged);
        if (bones == null) return null;
        Set<String> present = new LinkedHashSet<>();
        for (JsonElement element : bones) present.add(boneName(element));
        for (RootOutfits.Outfit outfit : worn) {
            for (RootOutfits.Piece piece : outfit.pieces()) {
                if (RigAssets.geometryPending(piece.model())) return null;
                JsonArray source = bonesOf(RigAssets.geometry(piece.model()));
                if (source == null) continue;
                Set<String> cut = new LinkedHashSet<>(piece.bones());
                boolean grew = true;
                while (grew) {
                    grew = false;
                    for (JsonElement element : source) {
                        String parent = boneParent(element);
                        if (!parent.isEmpty() && cut.contains(parent) && cut.add(boneName(element))) grew = true;
                    }
                }
                for (JsonElement element : source) {
                    String name = boneName(element);
                    if (cut.contains(name) && present.add(name)) bones.add(element.deepCopy());
                }
            }
        }
        return merged;
    }

    private static JsonArray bonesOf(JsonObject json) {
        if (json == null || !json.has("minecraft:geometry") || !json.get("minecraft:geometry").isJsonArray()) return null;
        JsonArray geometries = json.getAsJsonArray("minecraft:geometry");
        if (geometries.isEmpty() || !geometries.get(0).isJsonObject()) return null;
        JsonObject geometry = geometries.get(0).getAsJsonObject();
        return geometry.has("bones") && geometry.get("bones").isJsonArray() ? geometry.getAsJsonArray("bones") : null;
    }

    private static String boneName(JsonElement bone) {
        return bone.isJsonObject() && bone.getAsJsonObject().has("name")
                ? bone.getAsJsonObject().get("name").getAsString() : "";
    }

    private static String boneParent(JsonElement bone) {
        return bone.isJsonObject() && bone.getAsJsonObject().has("parent")
                ? bone.getAsJsonObject().get("parent").getAsString() : "";
    }

    /** Bake a full-body geometry and remember its bone parents so nested bones resolve by name. */
    private static ModelPart bakeGeometry(JsonObject json) {
        ModelPart part = BedrockGeometryLoader.parse(json, true);
        if (part == null) return null;
        Map<String, String> parents = new HashMap<>();
        JsonArray bones = bonesOf(json);
        if (bones != null) {
            for (JsonElement element : bones) parents.put(boneName(element), boneParent(element));
        }
        PARENTS.put(part, parents);
        return part;
    }

    /** Any mod's vanilla-style model class, built from the baked layer through its ModelPart constructor. */
    @SuppressWarnings("unchecked")
    private static EntityModel<LivingEntity> reflectModel(String className, ModelPart root) {
        try {
            Object model = Class.forName(className).getConstructor(ModelPart.class).newInstance(root);
            if (model instanceof EntityModel<?> entityModel) return (EntityModel<LivingEntity>) entityModel;
            Townstead.LOGGER.warn("Rig model class {} is not an entity model", className);
        } catch (Throwable t) {
            Townstead.LOGGER.warn("Rig model class {} could not be built: {}", className, t.toString());
        }
        return null;
    }

    /**
     * Run a generic model's own setupAnim. A mod's model may cast the entity to its own type; if it throws
     * with ours, the model is drawn at rest from then on (clips and poses still apply).
     */
    public static void setupAnim(EntityModel<LivingEntity> model, LivingEntity entity, float limbSwing,
                                 float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        if (STATIC_ONLY.contains(model)) return;
        try {
            model.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        } catch (RuntimeException e) {
            STATIC_ONLY.add(model);
            Townstead.LOGGER.warn("Rig model {} cannot animate this entity ({}); drawing it at rest",
                    model.getClass().getName(), e.toString());
        }
    }

    /** Mark the root drawn for a rig during its layer render (see {@link #ACTIVE}). */
    public static void beginRender(String rigBase, ModelPart root) {
        if (root != null) ACTIVE.put(rigBase, root);
    }

    public static void endRender(String rigBase) {
        ACTIVE.remove(rigBase);
    }

    /**
     * The parts from the root's child down to a named bone, or null if absent. Geometry bones resolve
     * through their authored parents (so a hand under body under main is found); a vanilla layer's
     * bones are direct children of the root.
     */
    public static ModelPart[] bonePath(ModelPart root, String name) {
        if (root == null || name == null || name.isEmpty()) return null;
        Map<String, ModelPart[]> paths = BONE_PATHS.computeIfAbsent(root, k -> new HashMap<>());
        if (paths.containsKey(name)) return paths.get(name);
        ModelPart[] path = resolvePath(root, name);
        paths.put(name, path);
        return path;
    }

    private static ModelPart[] resolvePath(ModelPart root, String name) {
        Map<String, String> parents = PARENTS.get(root);
        if (parents == null || !parents.containsKey(name)) {
            return root.hasChild(name) ? new ModelPart[]{root.getChild(name)} : null;
        }
        List<String> chain = new ArrayList<>();
        String cursor = name;
        while (cursor != null && !cursor.isEmpty() && chain.size() < 64) {
            chain.add(0, cursor);
            cursor = parents.get(cursor);
        }
        ModelPart[] path = new ModelPart[chain.size()];
        ModelPart part = root;
        for (int i = 0; i < chain.size(); i++) {
            if (!part.hasChild(chain.get(i))) return null;
            part = part.getChild(chain.get(i));
            path[i] = part;
        }
        return path;
    }

    /** The root a rig's bones resolve against right now: the variant being drawn, else the base. */
    private static ModelPart lookupRoot(String rigBase) {
        ModelPart active = ACTIVE.get(rigBase);
        return active != null ? active : ROOTS.get(rigBase);
    }

    /**
     * Move the pose stack from the model root to a bone's parent, so the caller's own
     * {@code bone.translateAndRotate} lands in the right frame for a nested bone. A no-op for a bone
     * directly under the root.
     */
    public static void translateToParent(String rigBase, String name, PoseStack pose) {
        ModelPart[] path = bonePath(lookupRoot(rigBase), name);
        if (path == null) return;
        for (int i = 0; i < path.length - 1; i++) path[i].translateAndRotate(pose);
    }

    /**
     * A bone's pose composed through its parents, in model pixels and radians
     * ({@code x, y, z, xRot, yRot, zRot}), so a nested bone reads like a root child. Null if absent.
     */
    public static float[] boneModelPose(String rigBase, String name) {
        return composedPose(rigBase, name, false);
    }

    public static float[] boneRestPose(String rigBase, String name) {
        return composedPose(rigBase, name, true);
    }

    private static float[] composedPose(String rigBase, String name, boolean rest) {
        ModelPart[] path = bonePath(lookupRoot(rigBase), name);
        if (path == null) return null;
        ModelPart bone = path[path.length - 1];
        Matrix4f matrix = new Matrix4f();
        for (ModelPart part : path) {
            var p = rest ? part.getInitialPose() : part.storePose();
            matrix.translate(p.x, p.y, p.z);
            matrix.rotateZYX(p.zRot, p.yRot, p.xRot);
        }
        Vector3f position = matrix.getTranslation(new Vector3f());
        Vector3f angles = matrix.getEulerAnglesZYX(new Vector3f());
        return new float[]{position.x, position.y, position.z, angles.x, angles.y, angles.z};
    }


    /**
     * Bake the rig's root part from its definition. An {@code entity_layer} rig bakes the named
     * vanilla/mod model layer's {@link LayerDefinition} directly (see {@link #bakeLayer}); a
     * {@code geometry} rig (custom {@code .geo.json}) is a later phase and bakes nothing yet.
     */
    private static ModelPart bakeRoot(String rigBase) {
        RigDefinition def = RootCatalogClient.rig(rigBase);
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
        ModelPart root = lookupRoot(rigBase);
        if (root == null) {
            model(rigBase);
            root = ROOTS.get(rigBase);
        }
        ModelPart[] path = bonePath(root, name);
        return path == null ? null : path[path.length - 1];
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
        ModelPart[] path = bonePath(lookupRoot(rigBase), name);
        return path == null ? null : path[path.length - 1];
    }

    /**
     * The already-baked root for a rig WITHOUT triggering a humanoid bake — safe for generic
     * (non-humanoid) rigs, whose root must first be baked via {@link #genericModel}. Returns null until
     * that has happened (the generic render branch bakes it before reading this).
     */
    public static ModelPart bakedRoot(String rigBase) {
        return lookupRoot(rigBase);
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
        String rootId = RootClientStore.resolve(entity);
        if (rootId == null || rootId.isEmpty()) return 1.0f;
        RootCatalogEntry origin = RootCatalogClient.origin(rootId);
        return origin == null || origin.rigScale() <= 0f ? 1.0f : origin.rigScale();
    }

    /** Whether this entity's species shows breasts (true unless a species opts out). */
    public static boolean breasts(LivingEntity entity) {
        String rootId = RootClientStore.resolve(entity);
        if (rootId == null || rootId.isEmpty()) return true;
        RootCatalogEntry origin = RootCatalogClient.origin(rootId);
        return origin == null || origin.breasts();
    }

    /** The species' per-state animation sources for this entity (humanoid default; never null). */
    public static Animations animations(LivingEntity entity) {
        String rootId = RootClientStore.resolve(entity);
        if (rootId == null || rootId.isEmpty()) return Animations.DEFAULT;
        RootCatalogEntry origin = RootCatalogClient.origin(rootId);
        return origin == null || origin.animations() == null ? Animations.DEFAULT : origin.animations();
    }

    /**
     * The rig's authored grip for the main or off hand, or null when that hand cannot hold (so its item
     * should not render). Null also when the entity has no synced rig.
     */
    public static Hold.Grip holdGrip(LivingEntity entity, boolean offHand) {
        RigDefinition def = RootCatalogClient.rig(rigBaseFor(entity));
        if (def == null || def.hold() == null) return null;
        return offHand ? def.hold().offhand() : def.hold().mainhand();
    }

    /** The rig's authored whole-body sleep orientation, or null when it rests upright with no lean. */
    public static RigDefinition.BodyPose sleepPose(String rigBase) {
        RigDefinition def = RootCatalogClient.rig(rigBase);
        return def == null ? null : def.bodyPose("sleep");
    }

    /** The rig's authored whole-body crawl/swim orientation, or null when it stays upright with no lean. */
    public static RigDefinition.BodyPose crawlPose(String rigBase) {
        RigDefinition def = RootCatalogClient.rig(rigBase);
        return def == null ? null : def.bodyPose("crawl");
    }

    /** A texture placeholder: {@code {name:N}}, one of N textures numbered from 0. */
    private static final java.util.regex.Pattern VARIANT = java.util.regex.Pattern.compile("\\{(\\w+):(\\d+)}");

    /**
     * The rig's texture for this entity. Each {@code {name:N}} placeholder takes the entity's form
     * variant of that name (see {@code StateForms}), so each one keeps their own look.
     */
    public static ResourceLocation texture(String rigBase, LivingEntity entity) {
        RigDefinition def = RootCatalogClient.rig(rigBase);
        if (def == null || def.texture() == null || def.texture().isEmpty()) return null;
        return RigAssets.texture(fill(def.texture(), entity));
    }

    /** Fills a texture's placeholders for this entity (0 for each, with no entity). */
    public static String fill(String template, LivingEntity entity) {
        if (template.indexOf('{') < 0) return template;
        java.util.regex.Matcher m = VARIANT.matcher(template);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            int count = Math.max(1, Integer.parseInt(m.group(2)));
            int value = entity == null ? 0 : Math.floorMod(
                    com.aetherianartificer.townstead.client.state.StateFormClient.variant(entity.getId(), m.group(1)), count);
            m.appendReplacement(out, Integer.toString(value));
        }
        m.appendTail(out);
        return out.toString();
    }

    /** Draws the rig's overlays (a werewolf's eyes) over a body already drawn with the same model. */
    public static void renderOverlays(String rigBase, LivingEntity entity, net.minecraft.client.model.EntityModel<?> model,
                                      com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffers,
                                      int light) {
        RigDefinition def = RootCatalogClient.rig(rigBase);
        if (def == null || def.overlays().isEmpty()) return;
        for (RigDefinition.Overlay overlay : def.overlays()) {
            ResourceLocation tex = RigAssets.texture(fill(overlay.texture(), entity));
            if (tex == null) continue;
            boolean glow = overlay.glowVariant().isEmpty() ? overlay.glow()
                    : com.aetherianartificer.townstead.client.state.StateFormClient.variant(entity.getId(), overlay.glowVariant()) != 0;
            com.mojang.blaze3d.vertex.VertexConsumer buffer = buffers.getBuffer(glow
                    ? net.minecraft.client.renderer.RenderType.eyes(tex)
                    : net.minecraft.client.renderer.RenderType.entityCutoutNoCull(tex));
            //? if neoforge {
            model.renderToBuffer(pose, buffer, light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, -1);
            //?} else {
            /*model.renderToBuffer(pose, buffer, light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, 1f);
            *///?}
        }
    }

    public static ResourceLocation texture(String rigBase) {
        RigDefinition def = RootCatalogClient.rig(rigBase);
        if (def == null || def.texture() == null || def.texture().isEmpty()) return null;
        if (def.texture().indexOf('{') >= 0) return RigAssets.texture(fill(def.texture(), null));
        // Prefer a datapack-synced texture (no resource pack needed); fall back to a plain resource
        // location for vanilla / resource-pack textures (e.g. minecraft:textures/entity/skeleton).
        return RigAssets.texture(def.texture());
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
        RigDefinition def = RootCatalogClient.rig(rigBase);
        if (def == null) return null;
        if (def.armorType() == RigDefinition.ArmorType.CUSTOM) {
            JsonObject geo = RigAssets.geometry(inner ? def.armorInner() : def.armorOuter());
            if (geo == null) return null;
            ModelPart armor = BedrockGeometryLoader.parse(geo, true);
            if (armor == null) return null;
            JsonArray parts = bonesOf(geo);
            if (parts != null) for (JsonElement entry : parts) {
                JsonObject part = entry.getAsJsonObject();
                String name = part.get("name").getAsString();
                if (!armor.hasChild(name) || !part.has("scale")) continue;
                JsonArray scale = part.getAsJsonArray("scale");
                if (scale.size() != 3) continue;
                ModelPart target = armor.getChild(name);
                target.xScale = scale.get(0).getAsFloat();
                target.yScale = scale.get(1).getAsFloat();
                target.zScale = scale.get(2).getAsFloat();
            }
            return armor;
        }
        if (def.armorType() != RigDefinition.ArmorType.LAYERS) return null;
        return bakeLayerRef(inner ? def.armorInner() : def.armorOuter());
    }
}
