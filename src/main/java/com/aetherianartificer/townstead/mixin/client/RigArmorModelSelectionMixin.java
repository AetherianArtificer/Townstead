package com.aetherianartificer.townstead.mixin.client;

import com.aetherianartificer.townstead.client.species.RigArmorRenderer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
//? if neoforge {
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
//?} else {
/*import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
*///?}

/** MCA's player selector also runs on our layer. Restore its fitted mesh after selection. */
@Mixin(HumanoidArmorLayer.class)
public abstract class RigArmorModelSelectionMixin {
    //? if neoforge {
    @Shadow @Final private HumanoidModel<?> innerModel;
    @Shadow @Final private HumanoidModel<?> outerModel;
    //?} else {
    /*@Shadow(remap = false) @Final private HumanoidModel<?> f_117071_;
    @Shadow(remap = false) @Final private HumanoidModel<?> f_117072_;
    *///?}

    // Modify the actual draw arguments, after other mods have selected a replacement model.
    // Wrapping getArmorModel would depend on wrapper order and MCA can skip that call entirely.
    //? if neoforge {
    @ModifyArgs(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/layers/HumanoidArmorLayer;renderArmorPiece(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;ILnet/minecraft/client/model/HumanoidModel;FFFFFF)V"), require = 4)
    private void townstead$keepFittedMesh(Args args) {
        args.set(5, RigArmorRenderer.preserveFittedModel(args.get(3), args.get(5), innerModel, outerModel));
    }
    //?} else {
    /*// WrapOperation, not ModifyArgs: Forge's module classloader cannot load ModifyArgs' synthetic Args classes.
    @WrapOperation(method = "m_6494_(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            remap = false, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/layers/HumanoidArmorLayer;m_117118_(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;ILnet/minecraft/client/model/HumanoidModel;)V"), require = 4)
    private void townstead$keepFittedMesh(HumanoidArmorLayer<?, ?, ?> layer, PoseStack pose, MultiBufferSource buffers,
                                          LivingEntity entity, EquipmentSlot slot, int light, HumanoidModel<?> model,
                                          Operation<Void> original) {
        original.call(layer, pose, buffers, entity, slot, light,
                RigArmorRenderer.preserveFittedModel(slot, model, f_117071_, f_117072_));
    }
    *///?}
}
