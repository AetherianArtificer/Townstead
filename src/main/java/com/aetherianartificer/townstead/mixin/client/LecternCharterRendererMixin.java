package com.aetherianartificer.townstead.mixin.client;

import com.aetherianartificer.townstead.politics.charter.CharterLecternAccess;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.LecternRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws a charter and civic cloth without putting either item into the lectern. */
@Mixin(LecternRenderer.class)
public abstract class LecternCharterRendererMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void townstead$renderCharter(LecternBlockEntity lectern, float partialTick, PoseStack pose,
                                          MultiBufferSource buffers, int light, int overlay, CallbackInfo ci) {
        if (!(lectern instanceof CharterLecternAccess access) || access.townstead$charterState() == CharterLecternAccess.NONE
                || lectern.getBlockState().getValue(LecternBlock.HAS_BOOK)) return;

        Direction facing = lectern.getBlockState().getValue(LecternBlock.FACING);
        float rotation = -facing.getClockWise().toYRot();
        var items = Minecraft.getInstance().getItemRenderer();

        pose.pushPose();
        pose.translate(0.5F, 1.075F, 0.5F);
        pose.mulPose(Axis.YP.rotationDegrees(rotation));
        pose.mulPose(Axis.XP.rotationDegrees(67.5F));
        pose.scale(0.72F, 0.72F, 0.72F);
        items.renderStatic(new ItemStack(Items.PAPER), ItemDisplayContext.FIXED,
                light, overlay, pose, buffers, lectern.getLevel(), 0);
        pose.popPose();

        pose.pushPose();
        pose.translate(0.5F, 0.72F, 0.5F);
        pose.mulPose(Axis.YP.rotationDegrees(rotation));
        pose.translate(0.0F, 0.0F, -0.405F);
        pose.scale(0.54F, 0.54F, 0.54F);
        ItemStack cloth = new ItemStack(access.townstead$charterState() == CharterLecternAccess.PREPARED
                ? Items.YELLOW_BANNER : Items.WHITE_BANNER);
        if (access.townstead$charterState() == CharterLecternAccess.FOUNDED && lectern.getLevel() != null)
            cloth = com.aetherianartificer.townstead.politics.heraldry.EmblemItems.banner(lectern.getLevel().registryAccess(),
                    com.aetherianartificer.townstead.politics.heraldry.EmblemRecipe.safe(access.townstead$emblem()));
        items.renderStatic(cloth, ItemDisplayContext.FIXED,
                light, overlay, pose, buffers, lectern.getLevel(), 1);
        pose.popPose();
    }
}
