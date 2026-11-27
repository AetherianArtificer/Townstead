package com.aetherianartificer.townstead.mixin.client;

import com.aetherianartificer.townstead.client.species.RigWearables;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Another mod's render layer drawing on a non-humanoid rig's host model (cyberware, worn add-ons)
 * draws on the humanoid parts as the rig's {@code wearables.fit} places them, so it sits on the rig's
 * own bones instead of floating where a human body would be. A part placed on several bones draws on
 * each, so the layer runs once per placement. Layers Townstead or MCA place themselves,
 * vanilla's, and the ones already fitted per item (Curios, Backpacked) draw as before.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class ForeignLayerRigFitMixin {

    @Unique
    private static final ClassValue<Boolean> townstead$FOREIGN = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            String name = type.getName();
            for (String own : new String[]{"net.minecraft.", "com.aetherianartificer.", "net.conczin.mca.",
                    "top.theillusivec4.curios.", "com.mrcrayfish.backpacked."}) {
                if (name.startsWith(own)) return false;
            }
            return true;
        }
    };

    /** Draws the layer once per fit placement (a cyber leg on each spider leg), or once as it would. */
    @Unique
    private static void townstead$render(LivingEntityRenderer<?, ?> renderer, RenderLayer<?, ?> layer,
                                         Entity entity, Runnable draw) {
        if (!(entity instanceof LivingEntity living) || !(renderer.getModel() instanceof HumanoidModel<?> host)
                || !townstead$FOREIGN.get(layer.getClass())) {
            draw.run();
            return;
        }
        int passes = RigWearables.fitPasses(living);
        if (passes == 0) {
            draw.run();
            return;
        }
        for (int pass = 0; pass < passes; pass++) {
            Runnable restore = RigWearables.fitForeign(living, host, pass);
            try {
                draw.run();
            } finally {
                if (restore != null) restore.run();
            }
        }
    }

    //? if neoforge {
    @WrapOperation(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/layers/RenderLayer;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/Entity;FFFFFF)V"),
            require = 0)
    //?} else {
    /*@WrapOperation(method = "m_7392_(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            remap = false, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/layers/RenderLayer;m_6494_(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/Entity;FFFFFF)V"),
            require = 0)
    *///?}
    private void townstead$fitForeignLayer(RenderLayer<?, ?> layer, PoseStack pose, MultiBufferSource buffers, int light,
                                           Entity entity, float limbSwing, float limbSwingAmount, float partialTick,
                                           float ageInTicks, float netHeadYaw, float headPitch, Operation<Void> original) {
        townstead$render((LivingEntityRenderer<?, ?>) (Object) this, layer, entity,
                () -> original.call(layer, pose, buffers, light, entity, limbSwing, limbSwingAmount, partialTick,
                        ageInTicks, netHeadYaw, headPitch));
    }
}
