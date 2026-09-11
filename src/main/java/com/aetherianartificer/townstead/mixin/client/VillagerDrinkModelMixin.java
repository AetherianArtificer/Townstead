package com.aetherianartificer.townstead.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.block.RenderShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Uses a drink's own placed model when its held-item model is only an inventory sprite. */
@Mixin(ItemInHandRenderer.class)
public abstract class VillagerDrinkModelMixin {
    // Both MCA's ordinary hands and Townstead's species grips use this render entry point.
    @Inject(method = "renderItem", at = @At("HEAD"), cancellable = true)
    private void townstead$preferDrinkGeometry(LivingEntity entity, ItemStack stack,
                                              ItemDisplayContext context, boolean leftHand,
                                              PoseStack pose, MultiBufferSource buffers, int light,
                                              CallbackInfo ci) {
        if (!(entity instanceof VillagerEntityMCA) || stack.isEmpty()
                || stack.getUseAnimation() != UseAnim.DRINK
                || !(stack.getItem() instanceof BlockItem drink)
                || (context != ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                    && context != ItemDisplayContext.THIRD_PERSON_RIGHT_HAND)) return;

        Minecraft minecraft = Minecraft.getInstance();
        var itemModel = minecraft.getItemRenderer().getModel(stack, entity.level(), entity, entity.getId());
        // Preserve authored 3D held models and custom renderers (including resource-pack overrides).
        if (itemModel.isGui3d() || itemModel.isCustomRenderer()) return;
        var state = drink.getBlock().defaultBlockState();
        //? if >=1.21 {
        var properties = stack.get(net.minecraft.core.component.DataComponents.BLOCK_STATE);
        if (properties != null) state = properties.apply(state);
        //?}
        if (state.getRenderShape() != RenderShape.MODEL) return;
        var blocks = minecraft.getBlockRenderer();
        var model = blocks.getBlockModel(state);
        if (model == minecraft.getModelManager().getMissingModel() || !model.isGui3d()
                || model.isCustomRenderer()) return;

        pose.pushPose();
        try {
            var transform = model.getTransforms().getTransform(context);
            if (transform == net.minecraft.client.renderer.block.model.ItemTransform.NO_TRANSFORM) {
                // Placed cups often have no item transforms. Center their base in the grip,
                // keeping the vessel upright at a hand-sized scale.
                pose.scale(0.75f, 0.75f, 0.75f);
                pose.translate(-0.5, -0.25, -0.5);
            } else {
                transform.apply(leftHand, pose);
                pose.translate(-0.5, -0.5, -0.5);
            }
            blocks.renderSingleBlock(state, pose, buffers, light, OverlayTexture.NO_OVERLAY);
        } finally {
            pose.popPose();
        }
        ci.cancel();
    }
}
