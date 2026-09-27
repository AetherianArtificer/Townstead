package com.aetherianartificer.townstead.mixin.compat.curios;

import com.aetherianartificer.townstead.client.species.RigWearables;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.ICurioRenderer;

/** Fits registered renderers which copy the host bones, without replacing the mod's item mesh. */
@Pseudo
@Mixin(targets = "top.theillusivec4.curios.client.render.CuriosLayer", remap = false)
public abstract class CuriosRigFitMixin {
    @WrapOperation(method = "*", at = @At(value = "INVOKE", target =
            "Ltop/theillusivec4/curios/api/client/ICurioRenderer;render(Lnet/minecraft/world/item/ItemStack;Ltop/theillusivec4/curios/api/SlotContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/entity/RenderLayerParent;Lnet/minecraft/client/renderer/MultiBufferSource;IFFFFFF)V"),
            remap = false, require = 1)
    private void townstead$fitRegisteredCurio(ICurioRenderer renderer, ItemStack stack, SlotContext slot,
            PoseStack pose, RenderLayerParent<?, ?> parent, MultiBufferSource buffers, int light,
            float swing, float amount, float partial, float age, float yaw, float pitch, Operation<Void> original) {
        Runnable restore = parent.getModel() instanceof HumanoidModel<?> host
                ? RigWearables.fitCurio(host, slot.entity(), slot.identifier(), BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())
                : () -> {};
        try {
            original.call(renderer, stack, slot, pose, parent, buffers, light, swing, amount, partial, age, yaw, pitch);
        } finally {
            restore.run();
        }
    }
}
