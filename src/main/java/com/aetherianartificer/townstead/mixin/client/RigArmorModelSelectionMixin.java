package com.aetherianartificer.townstead.mixin.client;

import com.aetherianartificer.townstead.client.species.RigArmorRenderer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** MCA's player selector also runs on our layer. Restore its fitted mesh after selection. */
@Mixin(HumanoidArmorLayer.class)
public abstract class RigArmorModelSelectionMixin {
    @Shadow @Final private HumanoidModel<?> innerModel;
    @Shadow @Final private HumanoidModel<?> outerModel;

    // Modify the actual draw arguments, after other mods have selected a replacement model.
    // Wrapping getArmorModel would depend on wrapper order and MCA can skip that call entirely.
    //? if neoforge {
    @ModifyArgs(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/layers/HumanoidArmorLayer;renderArmorPiece(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;ILnet/minecraft/client/model/HumanoidModel;FFFFFF)V"), require = 4)
    //?} else {
    /*@ModifyArgs(method = "m_6494_(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            remap = false, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/layers/HumanoidArmorLayer;m_117118_(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;ILnet/minecraft/client/model/HumanoidModel;)V"), require = 4)
    *///?}
    private void townstead$keepFittedMesh(Args args) {
        args.set(5, RigArmorRenderer.preserveFittedModel(args.get(3), args.get(5), innerModel, outerModel));
    }
}
