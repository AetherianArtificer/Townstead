package com.aetherianartificer.townstead.client.species;

import com.aetherianartificer.townstead.client.attachment.AttachmentClient;
import com.aetherianartificer.townstead.client.root.RootCatalogClient;
import com.aetherianartificer.townstead.client.root.RootClientStore;
import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.root.GeneCatalogEntry;
import com.aetherianartificer.townstead.root.RootCatalogEntry;
import com.aetherianartificer.townstead.root.gene.AllelePayload;
import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.client.render.layer.VillagerLayer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Renders an entity's expressed {@code skin_overlay} genes: player-format textures
 * painted onto the body between MCA's skin and face layers, so the detail sits ON
 * the skin and UNDER the eyes, clothing, and hair — orcish brows and noses,
 * wrinkles, freckles, scars, war paint. Textures are data-pack-synced (the same
 * named-texture pipeline rigs use); each overlay is optionally tinted flat, by the
 * bearer's resolved skin tone, or by their rendered hair colour. Inserted before
 * MCA's {@code FaceLayer} on a shell dilated between the skin and face layers.
 */
public class SkinOverlayLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends VillagerLayer<T, M> {

    public SkinOverlayLayer(RenderLayerParent<T, M> renderer, M model) {
        super(renderer, model);
    }

    @Override
    protected boolean isTranslucent() {
        return true;
    }

    @Override
    public void renderFinal(PoseStack transform, MultiBufferSource provider, int light, T entity,
                            float tickDelta, boolean visible, boolean glowing) {
        int overlay = LivingEntityRenderer.getOverlayCoords(entity, 0);
        for (String geneId : orderedOverlayGenes(entity)) {
            GeneCatalogEntry gene = RootCatalogClient.gene(geneId);
            ResourceLocation texture = resolveTexture(textureFor(entity, gene));
            if (texture == null) continue;
            int color = resolveTint(entity, gene.skinOverlayTint());
            draw(transform, provider, light, overlay, texture, color, visible, glowing);
        }
    }

    // Drawn by hand (the shipped MCA jars have no renderModel helper); only the
    // renderToBuffer colour signature differs by branch (int vs float components).
    private void draw(PoseStack transform, MultiBufferSource provider, int light, int overlayCoords,
                      ResourceLocation texture, int color, boolean visible, boolean glowing) {
        net.minecraft.client.renderer.RenderType layer =
                getRenderLayer(texture, visible, isTranslucent(), glowing);
        if (layer == null) return;
        com.mojang.blaze3d.vertex.VertexConsumer buffer = provider.getBuffer(layer);
        //? if neoforge {
        model.renderToBuffer(transform, buffer, light, overlayCoords, 0xFF000000 | (color & 0xFFFFFF));
        //?} else {
        /*float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        model.renderToBuffer(transform, buffer, light, overlayCoords, r, g, b, 1f);
        *///?}
    }

    /** The gene ids the entity could paint from: its expressed set, else its origin's grant list. */
    private static Set<String> overlayGenes(LivingEntity entity) {
        Set<String> expressed = RootClientStore.expressedGenes(entity);
        if (!expressed.isEmpty()) return expressed;
        String rootId = RootClientStore.resolve(entity);
        if (rootId.isEmpty()) return Set.of();
        RootCatalogEntry origin = RootCatalogClient.origin(rootId);
        if (origin == null) return Set.of();
        Set<String> out = new LinkedHashSet<>();
        for (RootCatalogEntry.Inherited inherited : origin.inheritedGenes()) out.add(inherited.geneId());
        return out;
    }

    /** Stable back-to-front order shared by third-person bodies and first-person hands. */
    private static List<String> orderedOverlayGenes(LivingEntity entity) {
        return overlayGenes(entity).stream()
                .filter(id -> {
                    GeneCatalogEntry gene = RootCatalogClient.gene(id);
                    return gene != null && gene.isSkinOverlay();
                })
                .sorted((left, right) -> {
                    GeneCatalogEntry a = RootCatalogClient.gene(left);
                    GeneCatalogEntry b = RootCatalogClient.gene(right);
                    int byOrder = Integer.compare(a.skinOverlayOrder(), b.skinOverlayOrder());
                    return byOrder != 0 ? byOrder : left.compareTo(right);
                })
                .toList();
    }

    /** The overlay texture id: the carried variant's own style when the gene has options. */
    private static String textureFor(LivingEntity entity, GeneCatalogEntry gene) {
        if (!gene.variants().isEmpty()) {
            String carried = AllelePayload.parse(
                    RootClientStore.resolveCarriedVariant(entity, gene.id())).variant();
            for (GeneCatalogEntry.Variant variant : gene.variants()) {
                if (variant.id().equals(carried) && !variant.texture().isEmpty()) return variant.texture();
            }
            for (GeneCatalogEntry.Variant variant : gene.variants()) {
                if (!variant.texture().isEmpty()) return variant.texture();
            }
        }
        return gene.skinOverlayTexture();
    }

    /** A data-pack-synced texture id to its DynamicTexture, else a plain resource location. */
    private static ResourceLocation resolveTexture(String id) {
        if (id == null || id.isEmpty()) return null;
        ResourceLocation synced = AttachmentClient.namedTexture(id);
        return synced != null ? synced : DataPackLang.parseId(id);
    }

    /** The overlay's ARGB tint: flat hex, the bearer's skin tone, their hair colour, or white. */
    private static int resolveTint(LivingEntity entity, String spec) {
        if (spec == null || spec.isEmpty()) return 0xFFFFFFFF;
        return switch (spec) {
            case "skin" -> 0xFF000000 | RigSkinColor.get(entity.getId(),
                    RigSkinTone.forEntity(entity) & 0xFFFFFF);
            case "hair" -> 0xFF000000 | (RigHairColor.get(entity.getId(), 0xFFFFFF) & 0xFFFFFF);
            default -> {
                String hex = spec.startsWith("#") ? spec.substring(1) : spec;
                try {
                    yield 0xFF000000 | (Integer.parseInt(hex, 16) & 0xFFFFFF);
                } catch (NumberFormatException e) {
                    yield 0xFFFFFFFF;
                }
            }
        };
    }

    /**
     * Paint the same expressed overlay textures onto Minecraft's separate first-person hand pass.
     * Entity render layers are not invoked by {@code PlayerRenderer.renderRightHand/renderLeftHand},
     * so without this explicit pass arm pixels only appear in third person.
     */
    public static void renderFirstPersonArm(PoseStack pose, MultiBufferSource buffers, int light,
                                            AbstractClientPlayer player,
                                            HumanoidModel<AbstractClientPlayer> renderedModel,
                                            boolean left) {
        // Use the renderer's actual arm, after every animation mod has posed it. A separately baked
        // shell drifts away whenever Fresh Player Animations replaces or transforms the hand model.
        ModelPart arm = left ? renderedModel.leftArm : renderedModel.rightArm;
        int layerIndex = 0;
        for (String geneId : orderedOverlayGenes(player)) {
            GeneCatalogEntry gene = RootCatalogClient.gene(geneId);
            ResourceLocation texture = resolveTexture(textureFor(player, gene));
            if (texture == null) continue;
            int color = resolveTint(player, gene.skinOverlayTint());
            com.mojang.blaze3d.vertex.VertexConsumer buffer =
                    buffers.getBuffer(RenderType.entityTranslucent(texture));

            // Expand around the already-animated arm's own pivot. This keeps Fresh Player's exact
            // rotation/translation while moving the overlay a fraction of a texel off the base
            // surface. Each additional overlay is slightly farther out to preserve stack order.
            float xScale = arm.xScale;
            float yScale = arm.yScale;
            float zScale = arm.zScale;
            float shell = 1.0025f + layerIndex++ * 0.001f;
            arm.xScale *= shell;
            arm.yScale *= shell;
            arm.zScale *= shell;
            try {
                //? if neoforge {
                arm.render(pose, buffer, light, OverlayTexture.NO_OVERLAY,
                        0xFF000000 | (color & 0xFFFFFF));
                //?} else {
                /*arm.render(pose, buffer, light, OverlayTexture.NO_OVERLAY,
                        ((color >> 16) & 0xFF) / 255f,
                        ((color >> 8) & 0xFF) / 255f,
                        (color & 0xFF) / 255f, 1f);
                *///?}
            } finally {
                arm.xScale = xScale;
                arm.yScale = yScale;
                arm.zScale = zScale;
            }
        }
    }
}
