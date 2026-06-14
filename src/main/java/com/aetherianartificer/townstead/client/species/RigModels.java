package com.aetherianartificer.townstead.client.species;

import com.aetherianartificer.townstead.client.origin.OriginCatalogClient;
import com.aetherianartificer.townstead.client.origin.OriginClientStore;
import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.origin.Hold;
import com.aetherianartificer.townstead.origin.OriginCatalogEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;

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

    private RigModels() {}

    /** The species rig.base for an entity, resolved through its synced origin (villager default). */
    public static String rigBaseFor(LivingEntity entity) {
        String originId = OriginClientStore.get(entity.getId());
        if (originId == null || originId.isEmpty()) return VILLAGER;
        OriginCatalogEntry origin = OriginCatalogClient.origin(originId);
        return origin == null || origin.rigBase() == null || origin.rigBase().isEmpty()
                ? VILLAGER : origin.rigBase();
    }

    /** True when the rig is a supported non-villager model (so the swap should engage). */
    public static boolean isAlternate(String rigBase) {
        return rigBase != null && !rigBase.equals(VILLAGER) && layerFor(rigBase) != null;
    }

    /** The cached humanoid model for a rig, baked from its vanilla model layer; null if unsupported. */
    public static HumanoidModel<LivingEntity> model(String rigBase) {
        if (MODELS.containsKey(rigBase)) return MODELS.get(rigBase);
        ModelLayerLocation layer = layerFor(rigBase);
        HumanoidModel<LivingEntity> model = null;
        if (layer != null) {
            ModelPart part = Minecraft.getInstance().getEntityModels().bakeLayer(layer);
            model = new HumanoidModel<>(part);
            ROOTS.put(rigBase, part);
        }
        MODELS.put(rigBase, model);
        return model;
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
        String originId = OriginClientStore.get(entity.getId());
        if (originId == null || originId.isEmpty()) return 1.0f;
        OriginCatalogEntry origin = OriginCatalogClient.origin(originId);
        return origin == null || origin.rigScale() <= 0f ? 1.0f : origin.rigScale();
    }

    /**
     * The species' authored grip for the main or off hand, or null when that hand cannot hold (so
     * its item should not render). Null also when the entity has no synced species.
     */
    public static Hold.Grip holdGrip(LivingEntity entity, boolean offHand) {
        String originId = OriginClientStore.get(entity.getId());
        if (originId == null || originId.isEmpty()) return null;
        OriginCatalogEntry origin = OriginCatalogClient.origin(originId);
        if (origin == null || origin.hold() == null) return null;
        return offHand ? origin.hold().offhand() : origin.hold().mainhand();
    }

    public static ResourceLocation texture(String rigBase) {
        return switch (rigBase) {
            case "minecraft:skeleton" -> DataPackLang.parseId("minecraft:textures/entity/skeleton/skeleton.png");
            case "minecraft:zombie" -> DataPackLang.parseId("minecraft:textures/entity/zombie/zombie.png");
            case "minecraft:husk" -> DataPackLang.parseId("minecraft:textures/entity/zombie/husk.png");
            default -> null;
        };
    }

    private static ModelLayerLocation layerFor(String rigBase) {
        return switch (rigBase) {
            case "minecraft:skeleton" -> ModelLayers.SKELETON;
            case "minecraft:zombie" -> ModelLayers.ZOMBIE;
            case "minecraft:husk" -> ModelLayers.HUSK;
            default -> null;
        };
    }
}
