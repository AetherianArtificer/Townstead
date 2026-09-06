package com.aetherianartificer.townstead.client.species;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.client.attachment.geo.BedrockGeometryLoader;
import com.aetherianartificer.townstead.compat.curios.CuriosClientCompat;
import com.aetherianartificer.townstead.compat.curios.CuriosCompat;
import com.aetherianartificer.townstead.compat.curios.GraftedWearableLayers;
import net.minecraft.world.item.ItemDisplayContext;
import com.aetherianartificer.townstead.item.Wearable;
import com.aetherianartificer.townstead.root.rig.RigDefinition;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Renders an entity's worn {@link Wearable} (currently the scarf) as a real 3D mesh, separate from its
 * flat inventory icon. The mesh binds its own entity texture directly (no item atlas, so a 32×32 greyscale
 * texture works and never magentas), tinted by the dye colour, and anchors to the host {@code head}/{@code
 * body} bone. {@code RigWearables} re-poses that host bone onto a non-humanoid rig's real head/back during
 * {@code setupAnim}, so the wearable follows any body; a rig fine-tunes the seat with a per-item delta under
 * its {@code wearables.<channel>.items.<item id>}, while a humanoid uses the item's own default seat.
 *
 * <p>The scarf is the only wearer for now; the flat item model hides itself in the HEAD display context
 * (scale 0) so vanilla's {@code CustomHeadLayer} draws nothing on a player head and only this mesh shows.</p>
 */
public class WornItemLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {

    private static final Map<ResourceLocation, Optional<ModelPart>> GEO = new ConcurrentHashMap<>();
    /** Worn placement of a block-space model, matching how backpack mods seat their own model on the body. */
    private static final float BLOCK_MODEL_WORN_SCALE = 1.05f;
    private static final float BLOCK_MODEL_WORN_LIFT = -0.06f;
    private static final float BLOCK_MODEL_WORN_STANDOFF = 0.0625f;

    public WornItemLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, T entity,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        if (entity.isInvisible()) return;

        ItemStack head = entity.getItemBySlot(EquipmentSlot.HEAD);
        if (head.getItem() instanceof Wearable wearable) {
            renderWearable(wearable, head, pose, buffers, light, entity);
        }
        // Curios slots ride alongside the head slot, for players and villagers alike. On a villager, a
        // curio that no one else draws (no Curios renderer, no grafted mod layer) is seated on the body as
        // its own item; a player keeps whatever its mod's own player-only layer draws.
        boolean seatOrphans = !(entity instanceof Player);
        CuriosCompat.forEachWornVisible(entity, (slotId, stack) -> {
            if (stack.getItem() instanceof Wearable wearable) {
                renderWearable(wearable, stack, pose, buffers, light, entity);
            } else if (seatOrphans && !CuriosClientCompat.hasRenderer(stack) && !GraftedWearableLayers.claims(stack)) {
                CurioItemSeat seat = CurioItemSeat.forSlot(slotId);
                if (seat != null) renderSeatedItem(seat, stack, pose, buffers, light, entity);
            }
        });
    }

    private void renderWearable(Wearable wearable, ItemStack stack, PoseStack pose, MultiBufferSource buffers,
                                int light, T entity) {
        ModelPart geometry = geometry(wearable.wornGeo());
        if (geometry == null) return;
        ModelPart bone = boneFor(wearable.anchorChannel());
        if (bone == null) return;

        float[] offset;
        float[] rotation;
        RigDefinition.Adjust delta = rigDelta(entity, wearable.anchorChannel(), itemId(stack));
        if (delta != null) {
            offset = delta.offset();
            rotation = delta.rotation();
        } else {
            float[][] seat = wearable.humanoidSeat();
            offset = seat[0];
            rotation = seat[1];
        }

        pose.pushPose();
        bone.translateAndRotate(pose);
        pose.translate(offset[0] / 16f, offset[1] / 16f, offset[2] / 16f);
        if (rotation[2] != 0f) pose.mulPose(Axis.ZP.rotationDegrees(rotation[2]));
        if (rotation[1] != 0f) pose.mulPose(Axis.YP.rotationDegrees(rotation[1]));
        if (rotation[0] != 0f) pose.mulPose(Axis.XP.rotationDegrees(rotation[0]));
        float scale = wearable.wornScale();
        if (scale != 1f) pose.scale(scale, scale, scale);

        VertexConsumer buffer = buffers.getBuffer(RenderType.entityCutoutNoCull(wearable.wornTexture()));
        renderPart(geometry, pose, buffer, light, wearable.wornColor(stack));
        pose.popPose();
    }

    /**
     * Draws a plain item at its slot's seat: the humanoid seat, or on a generic rig the re-posed host
     * bone plus the rig's per-item delta, the same anchoring a {@link Wearable} gets.
     *
     * <p>An item with its own renderer (a backpack mod's 3D backpack, say) is a block-space model meant
     * to be worn at natural size, so it is flipped into entity space and drawn untransformed, the way
     * such mods' own player layers draw it. An ordinary item goes through the fixed item transform and
     * the seat's scale, which reads as the item pinned to the body.</p>
     */
    private void renderSeatedItem(CurioItemSeat seat, ItemStack stack, PoseStack pose, MultiBufferSource buffers,
                                  int light, T entity) {
        ModelPart bone = boneFor(seat.channel());
        if (bone == null) return;
        RigDefinition.Adjust delta = rigDelta(entity, seat.channel(), itemId(stack));
        float[] rotation = delta != null ? delta.rotation() : seat.rotation();
        var itemRenderer = Minecraft.getInstance().getItemRenderer();
        boolean ownRenderer = itemRenderer.getModel(stack, entity.level(), entity, 0).isCustomRenderer();

        pose.pushPose();
        bone.translateAndRotate(pose);
        if (!ownRenderer || delta != null) {
            float[] offset = delta != null ? delta.offset() : seat.offset();
            pose.translate(offset[0] / 16f, offset[1] / 16f, offset[2] / 16f);
        }
        if (rotation[2] != 0f) pose.mulPose(Axis.ZP.rotationDegrees(rotation[2]));
        if (rotation[1] != 0f) pose.mulPose(Axis.YP.rotationDegrees(rotation[1]));
        if (rotation[0] != 0f) pose.mulPose(Axis.XP.rotationDegrees(rotation[0]));
        if (ownRenderer) {
            // Block space (Y up, Z forward) into entity space (Y down, Z back), at worn size.
            pose.mulPose(Axis.XP.rotationDegrees(180f));
            pose.scale(BLOCK_MODEL_WORN_SCALE, BLOCK_MODEL_WORN_SCALE, BLOCK_MODEL_WORN_SCALE);
            pose.translate(0f, BLOCK_MODEL_WORN_LIFT, BLOCK_MODEL_WORN_STANDOFF);
            itemRenderer.renderStatic(stack, ItemDisplayContext.NONE, light,
                    OverlayTexture.NO_OVERLAY, pose, buffers, entity.level(), entity.getId());
        } else {
            pose.scale(seat.scale(), seat.scale(), seat.scale());
            itemRenderer.renderStatic(stack, ItemDisplayContext.FIXED, light,
                    OverlayTexture.NO_OVERLAY, pose, buffers, entity.level(), entity.getId());
        }
        pose.popPose();
    }

    private ModelPart boneFor(String channel) {
        M model = getParentModel();
        return channel.equals("body") ? model.body : model.head;
    }

    /**
     * The seat delta for a generic rig: its per-item {@code wearables.<channel>.items.<item id>} delta, or
     * {@link RigDefinition.Adjust#ZERO} when the rig has the channel but no item override (the host bone is
     * already re-posed onto the rig, so zero sits the mesh on it). Returns null for a humanoid wearer, so the
     * caller falls back to the item's own seat.
     */
    private static RigDefinition.Adjust rigDelta(LivingEntity entity, String channel, String itemId) {
        String rigBase = RigModels.rigBaseFor(entity);
        if (!RigModels.isGeneric(rigBase)) return null;
        RigDefinition def = RigModels.definition(rigBase);
        if (def == null) return null;
        RigDefinition.WornAnchor anchor = channel.equals("body") ? def.back() : def.head();
        if (anchor == null) return RigDefinition.Adjust.ZERO;
        RigDefinition.Adjust delta = anchor.items().get(itemId);
        return delta != null ? delta : RigDefinition.Adjust.ZERO;
    }

    private static String itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private static ModelPart geometry(ResourceLocation geo) {
        return GEO.computeIfAbsent(geo, WornItemLayer::bake).orElse(null);
    }

    private static Optional<ModelPart> bake(ResourceLocation geo) {
        try {
            var resource = Minecraft.getInstance().getResourceManager().getResource(geo).orElse(null);
            if (resource == null) return Optional.empty();
            try (var in = resource.open();
                 InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                return Optional.ofNullable(BedrockGeometryLoader.parse(root));
            }
        } catch (Exception e) {
            Townstead.LOGGER.error("Failed to bake worn geometry {}", geo, e);
            return Optional.empty();
        }
    }

    private static void renderPart(ModelPart part, PoseStack pose, VertexConsumer buffer, int light, int tint) {
        int color = 0xFF000000 | tint;
        //? if neoforge {
        part.render(pose, buffer, light, OverlayTexture.NO_OVERLAY, color);
        //?} else {
        /*float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = ((color >> 24) & 0xFF) / 255f;
        part.render(pose, buffer, light, OverlayTexture.NO_OVERLAY, r, g, b, a);
        *///?}
    }
}
