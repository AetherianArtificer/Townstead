package com.aetherianartificer.townstead.mixin.client;

import com.aetherianartificer.townstead.client.livery.LiveryRender;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
//? if neoforge {
import net.minecraft.world.item.ArmorMaterial;
//?} else {
/*import net.minecraft.world.item.ArmorItem;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
*///?}

/**
 * Livery on armour, drawn without changing the item: the layer draws a display copy carrying the
 * style's trims and dye, each armour texture may be swapped for the style's art and tinted, and the
 * style's overlays are drawn over the base layer with the same model.
 * Players and villagers alike pass through here; anyone without a livery is untouched.
 */
@Mixin(HumanoidArmorLayer.class)
public abstract class LiveryArmorLayerMixin {

    //? if neoforge {
    @Redirect(method = "renderArmorPiece(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;ILnet/minecraft/client/model/HumanoidModel;FFFFFF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getItemBySlot(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;"), require = 1)
    private ItemStack townstead$liveryStack(LivingEntity entity, EquipmentSlot slot) {
        return LiveryRender.displayStack(entity, slot, entity.getItemBySlot(slot));
    }

    @Redirect(method = "renderArmorPiece(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;ILnet/minecraft/client/model/HumanoidModel;FFFFFF)V",
            at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/client/ClientHooks;getArmorTexture(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ArmorMaterial$Layer;ZLnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/resources/ResourceLocation;", remap = false), require = 1)
    private ResourceLocation townstead$liveryTexture(Entity entity, ItemStack stack, ArmorMaterial.Layer layer, boolean inner, EquipmentSlot slot) {
        return LiveryRender.texture(entity, net.neoforged.neoforge.client.ClientHooks.getArmorTexture(entity, stack, layer, inner, slot));
    }

    @WrapOperation(method = "renderArmorPiece(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;ILnet/minecraft/client/model/HumanoidModel;FFFFFF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/layers/HumanoidArmorLayer;renderModel(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/model/Model;ILnet/minecraft/resources/ResourceLocation;)V"),
            require = 1)
    private void townstead$liveryDraw(HumanoidArmorLayer<?, ?, ?> layer, PoseStack pose, MultiBufferSource buffers,
                                      int light, Model model, int colour, ResourceLocation texture, Operation<Void> original) {
        original.call(layer, pose, buffers, light, model, LiveryRender.tint(colour), texture);
        for (LiveryRender.Overlay overlay : LiveryRender.overlays()) {
            original.call(layer, pose, buffers, light, model, overlay.argb(), overlay.texture());
        }
    }
    //?} else {
    /*@Redirect(method = "m_117118_(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;ILnet/minecraft/client/model/HumanoidModel;)V",
            remap = false, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;m_6844_(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;"), require = 1)
    private ItemStack townstead$liveryStack(LivingEntity entity, EquipmentSlot slot) {
        return LiveryRender.displayStack(entity, slot, entity.getItemBySlot(slot));
    }

    @Inject(method = "getArmorResource", remap = false, at = @At("RETURN"), cancellable = true, require = 1)
    private void townstead$liveryTexture(Entity entity, ItemStack stack, EquipmentSlot slot, String type,
                                         CallbackInfoReturnable<ResourceLocation> cir) {
        cir.setReturnValue(LiveryRender.texture(entity, cir.getReturnValue()));
    }

    // WrapOperation, not ModifyArgs: Forge's module classloader cannot load ModifyArgs' synthetic Args classes.
    @WrapOperation(method = "m_117118_(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;ILnet/minecraft/client/model/HumanoidModel;)V",
            remap = false, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/layers/HumanoidArmorLayer;renderModel(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/item/ArmorItem;Lnet/minecraft/client/model/Model;ZFFFLnet/minecraft/resources/ResourceLocation;)V"),
            require = 1)
    private void townstead$liveryTint(HumanoidArmorLayer<?, ?, ?> layer, PoseStack pose, MultiBufferSource buffers,
                                      int light, ArmorItem item, Model model, boolean glint,
                                      float r, float g, float b, ResourceLocation texture, Operation<Void> original) {
        int tint = LiveryRender.tint(0);
        if (tint != 0) {
            r = (tint >> 16 & 255) / 255f;
            g = (tint >> 8 & 255) / 255f;
            b = (tint & 255) / 255f;
        }
        original.call(layer, pose, buffers, light, item, model, glint, r, g, b, texture);
        for (LiveryRender.Overlay overlay : LiveryRender.overlays()) {
            int argb = overlay.argb();
            original.call(layer, pose, buffers, light, item, model, glint,
                    (argb >> 16 & 255) / 255f, (argb >> 8 & 255) / 255f, (argb & 255) / 255f, overlay.texture());
        }
    }
    *///?}
}
