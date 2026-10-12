package com.aetherianartificer.townstead.client.species;

import com.aetherianartificer.townstead.client.root.RootCatalogClient;
import com.aetherianartificer.townstead.client.root.RootClientStore;
import com.aetherianartificer.townstead.root.RootCatalogEntry;
import com.aetherianartificer.townstead.root.RootLook;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.conczin.mca.client.model.CommonVillagerModel;
import net.conczin.mca.entity.VillagerLike;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * MCA clothing on a rig body: the skin the villager wears (MCA's own clothes value, chosen by
 * profession, wardrobe or the editor) drawn over the posed rig, like MCA's clothing layer over its
 * villager body. Only a skin the root's {@code body_clothing} lists as fitting this body is drawn,
 * since a skin painted for MCA's villager would land on the wrong UVs.
 */
public final class RigSkins {

    private RigSkins() {}

    public static <T extends LivingEntity> void render(T entity, EntityModel<LivingEntity> model, PoseStack pose,
                                                       MultiBufferSource buffers, int light, float fade) {
        ResourceLocation texture = texture(entity);
        if (texture == null) return;
        int alpha = Math.round(Math.max(0f, Math.min(1f, fade)) * 255f);
        VertexConsumer buffer = buffers.getBuffer(RenderType.entityTranslucent(texture));
        // A rig may limit which parts a worn skin covers (a legless ghost, thin arms whose UVs don't
        // match a villager skin's): hide the rest for this pass, then put them back.
        java.util.List<net.minecraft.client.model.geom.ModelPart> hidden = hideUnskinned(entity, model);
        try {
            draw(model, pose, buffer, light, alpha);
        } finally {
            for (net.minecraft.client.model.geom.ModelPart part : hidden) part.visible = true;
        }
    }

    private static void draw(EntityModel<LivingEntity> model, PoseStack pose, VertexConsumer buffer, int light, int alpha) {
        //? if neoforge {
        model.renderToBuffer(pose, buffer, light, OverlayTexture.NO_OVERLAY, (alpha << 24) | 0xFFFFFF);
        //?} else {
        /*model.renderToBuffer(pose, buffer, light, OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, alpha / 255f);
        *///?}
    }

    /**
     * Hides the humanoid parts the entity's rig doesn't let a worn skin cover ({@code skin_parts}),
     * returning the parts it hid so the caller can show them again. Nothing is hidden when the rig
     * names no parts or isn't a humanoid model.
     */
    private static java.util.List<net.minecraft.client.model.geom.ModelPart> hideUnskinned(LivingEntity entity,
                                                                                        EntityModel<LivingEntity> model) {
        java.util.List<net.minecraft.client.model.geom.ModelPart> hidden = new java.util.ArrayList<>();
        com.aetherianartificer.townstead.root.rig.RigDefinition def = RootCatalogClient.rig(RigModels.rigBaseFor(entity));
        if (def == null || def.skinParts().isEmpty()
                || !(model instanceof net.minecraft.client.model.HumanoidModel<?> humanoid)) return hidden;
        java.util.Map<String, net.minecraft.client.model.geom.ModelPart> parts = java.util.Map.of(
                "head", humanoid.head, "headwear", humanoid.hat, "body", humanoid.body,
                "right_arm", humanoid.rightArm, "left_arm", humanoid.leftArm,
                "right_leg", humanoid.rightLeg, "left_leg", humanoid.leftLeg);
        for (var entry : parts.entrySet()) {
            boolean allowed = def.skinParts().contains(entry.getKey())
                    || ("headwear".equals(entry.getKey()) && def.skinParts().contains("head"));
            if (!allowed && entry.getValue().visible) {
                entry.getValue().visible = false;
                hidden.add(entry.getValue());
            }
        }
        return hidden;
    }

    /**
     * Each step of {@link #texture} for one entity, for {@code /townstead-roots-client}: where an
     * outfit stops being drawn on a rig body.
     */
    public static String describe(LivingEntity entity) {
        StringBuilder out = new StringBuilder("outfit: ");
        String rootId = RootClientStore.resolve(entity);
        RootLook look = RootCatalogClient.look(rootId);
        out.append("root=").append(rootId).append(" look=")
                .append(look == null ? "none" : look.skins().toString());
        RootCatalogEntry origin = RootCatalogClient.origin(rootId);
        out.append(" rigBase[root]=").append(origin == null ? "no origin" : origin.rigBase())
                .append(" rigBase[entity]=").append(RigModels.rigBaseFor(entity))
                .append(" alternate=").append(RigModels.isAlternate(RigModels.rigBaseFor(entity)))
                .append(" generic=").append(RigModels.isGeneric(RigModels.rigBaseFor(entity)));
        VillagerLike<?> villager = CommonVillagerModel.getVillager(entity);
        String clothes = villager == null ? null : villager.getClothes();
        out.append(" villagerLike=").append(villager == null ? "none" : villager.getClass().getSimpleName())
                .append(" clothes=").append(clothes)
                .append(" fits=").append(look != null && look.fits(clothes));
        ResourceLocation tex = texture(entity);
        out.append(" texture=").append(tex);
        if (tex != null) {
            out.append(" exists=").append(net.minecraft.client.Minecraft.getInstance().getResourceManager()
                    .getResource(tex).isPresent());
        }
        return out.toString();
    }

    /** The fitted skin texture this entity wears on its species body, or null. */
    @Nullable
    static ResourceLocation texture(LivingEntity entity) {
        String rootId = RootClientStore.resolve(entity);
        RootLook look = RootCatalogClient.look(rootId);
        if (look == null || look.skins().isEmpty()) return null;
        // Skins fit the species body; a life-stage rig (an egg) is a different body.
        RootCatalogEntry origin = RootCatalogClient.origin(rootId);
        if (origin == null || !RigModels.rigBaseFor(entity).equals(origin.rigBase())) return null;
        VillagerLike<?> villager = CommonVillagerModel.getVillager(entity);
        if (villager == null) return null;
        String clothes = villager.getClothes();
        if (!look.fits(clothes)) return null;
        // the body may draw each outfit as its own counterpart (a zombie's tattered version)
        com.aetherianartificer.townstead.root.rig.RigDefinition def = RigModels.definition(origin.rigBase());
        if (def != null && !def.skinRemap().isEmpty()) {
            String drawn = clothes;
            for (var swap : def.skinRemap().entrySet()) drawn = drawn.replace(swap.getKey(), swap.getValue());
            if (!drawn.equals(clothes)) {
                ResourceLocation swapped = RigAssets.texture(drawn);
                if (swapped != null && net.minecraft.client.Minecraft.getInstance().getResourceManager()
                        .getResource(swapped).isPresent()) {
                    return swapped;
                }
            }
        }
        return RigAssets.texture(clothes);
    }
}
