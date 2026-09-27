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
        //? if neoforge {
        model.renderToBuffer(pose, buffer, light, OverlayTexture.NO_OVERLAY, (alpha << 24) | 0xFFFFFF);
        //?} else {
        /*model.renderToBuffer(pose, buffer, light, OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, alpha / 255f);
        *///?}
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
        return look.fits(clothes) ? RigAssets.texture(clothes) : null;
    }
}
