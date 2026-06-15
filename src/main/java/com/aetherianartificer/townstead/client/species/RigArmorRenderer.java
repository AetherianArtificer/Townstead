package com.aetherianartificer.townstead.client.species;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;

/**
 * Renders an alternate-rig villager's worn armor fitted to the rig instead of MCA's villager-shaped
 * host armor. MCA poses its host armor by copying from the villager body model and draws it at the
 * host's scale, so on a skeleton it floats as a wider, out-of-sync silhouette. Here we drive a real
 * vanilla {@link HumanoidArmorLayer} whose parent model is the (already posed) rig and whose armor
 * models are humanoid-proportioned, and call its {@code render} inside {@link SpeciesRigLayer}'s
 * scaled pose. That reuses all of vanilla's armor handling (textures, trims, dyes, glint) while the
 * armor matches the rig's shape, pose, and scale exactly.
 *
 * <p>MCA's host armor is suppressed for these entities by {@code HostArmorSuppressMixin}, which lets
 * our own draw through via the {@link #isRendering()} guard. The player path is untouched: only the
 * villager host enables this (see {@code SpeciesRigLayer}'s {@code renderArmor} flag), and the
 * suppressor skips players.</p>
 */
public final class RigArmorRenderer {

    // True only while our own armor layer is drawing, so the host-armor suppressor lets it through
    // instead of cancelling it as if it were MCA's host armor (both are HumanoidArmorLayer).
    private static final ThreadLocal<Boolean> RENDERING = ThreadLocal.withInitial(() -> false);

    // One armor layer per rig base; its parent returns the shared (per-frame posed) rig model.
    private static final Map<String, HumanoidArmorLayer<LivingEntity, HumanoidModel<LivingEntity>, HumanoidModel<LivingEntity>>> LAYERS = new HashMap<>();

    // Vanilla armor cube deformations: inner (leggings/boots) snug, outer (helmet/chest/arms) larger.
    private static final float INNER_DEFORM = 0.5f;
    private static final float OUTER_DEFORM = 1.0f;

    private RigArmorRenderer() {}

    /** True while this renderer is drawing, so the host-armor suppressor passes our own draw through. */
    public static boolean isRendering() {
        return RENDERING.get();
    }

    /**
     * Draw the entity's worn armor on the rig. The rig model must already be posed for this frame
     * (the armor layer copies its bone transforms); call inside the rig's scaled pose.
     */
    public static void render(LivingEntity entity, String rigBase, HumanoidModel<LivingEntity> rig,
                              ResourceLocation texture, PoseStack pose, MultiBufferSource buffers, int light,
                              float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                              float netHeadYaw, float headPitch) {
        HumanoidArmorLayer<LivingEntity, HumanoidModel<LivingEntity>, HumanoidModel<LivingEntity>> layer =
                LAYERS.computeIfAbsent(rigBase, b -> buildLayer(b, rig, texture));
        RENDERING.set(true);
        try {
            layer.render(pose, buffers, light, entity, limbSwing, limbSwingAmount, partialTick, ageInTicks, netHeadYaw, headPitch);
        } finally {
            RENDERING.set(false);
        }
    }

    private static HumanoidArmorLayer<LivingEntity, HumanoidModel<LivingEntity>, HumanoidModel<LivingEntity>> buildLayer(
            String rigBase, HumanoidModel<LivingEntity> rig, ResourceLocation texture) {
        RenderLayerParent<LivingEntity, HumanoidModel<LivingEntity>> parent = new RigParent(rig, texture);
        return new HumanoidArmorLayer<>(parent, armorModel(rigBase, true), armorModel(rigBase, false),
                Minecraft.getInstance().getModelManager());
    }

    /**
     * The armor model for a rig: the rig's own armor layer when it has one (e.g. a skeleton's thin
     * arms and legs, so leggings are not baggy), baked via {@link RigModels#bakeArmorPart} which
     * bypasses EMF's {@code bakeLayer} the same way the body does. Falls back to a generic
     * humanoid-proportioned mesh (as MCA does for its own armor) for rigs without dedicated layers.
     */
    private static HumanoidModel<LivingEntity> armorModel(String rigBase, boolean inner) {
        ModelPart part = RigModels.bakeArmorPart(rigBase, inner);
        if (part != null) return new HumanoidModel<>(part);
        return new HumanoidModel<>(LayerDefinition.create(
                HumanoidModel.createMesh(new CubeDeformation(inner ? INNER_DEFORM : OUTER_DEFORM), 0.0f), 64, 32).bakeRoot());
    }

    /** Minimal render parent so the vanilla armor layer poses from the rig model and uses its texture. */
    private record RigParent(HumanoidModel<LivingEntity> model, ResourceLocation texture)
            implements RenderLayerParent<LivingEntity, HumanoidModel<LivingEntity>> {
        @Override
        public HumanoidModel<LivingEntity> getModel() {
            return model;
        }

        @Override
        public ResourceLocation getTextureLocation(LivingEntity entity) {
            return texture;
        }
    }
}
