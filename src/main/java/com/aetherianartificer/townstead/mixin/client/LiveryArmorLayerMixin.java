package com.aetherianartificer.townstead.mixin.client;

import com.aetherianartificer.townstead.client.livery.LiveryRender;
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
import org.spongepowered.asm.mixin.injection.ModifyArg;
//?} else {
/*import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
*///?}

/**
 * Livery on armour, drawn without changing the item: the layer draws a display copy carrying the
 * style's trims and dye, and each armour texture may be swapped for the style's art and tinted.
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

    @ModifyArg(method = "renderArmorPiece(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;ILnet/minecraft/client/model/HumanoidModel;FFFFFF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/layers/HumanoidArmorLayer;renderModel(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/model/Model;ILnet/minecraft/resources/ResourceLocation;)V"),
            index = 4, require = 1)
    private int townstead$liveryTint(int colour) {
        return LiveryRender.tint(colour);
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

    @ModifyArgs(method = "m_117118_(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;ILnet/minecraft/client/model/HumanoidModel;)V",
            remap = false, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/layers/HumanoidArmorLayer;renderModel(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/item/ArmorItem;Lnet/minecraft/client/model/Model;ZFFFLnet/minecraft/resources/ResourceLocation;)V"),
            require = 1)
    private void townstead$liveryTint(Args args) {
        int tint = LiveryRender.tint(0);
        if (tint == 0) return;
        args.set(6, (tint >> 16 & 255) / 255f);
        args.set(7, (tint >> 8 & 255) / 255f);
        args.set(8, (tint & 255) / 255f);
    }
    *///?}
}
