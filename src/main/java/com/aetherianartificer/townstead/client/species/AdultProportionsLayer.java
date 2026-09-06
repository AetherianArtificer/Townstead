package com.aetherianartificer.townstead.client.species;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Runs first in the villager's layer stack and clears the model's vanilla {@code young} flag. MCA sizes
 * children by scaling the whole villager and ignores that flag, but the renderer still sets it for any
 * baby, and every stock humanoid model that copies its pose from MCA's (Curios renderers, wearable mod
 * layers) then applies vanilla's baby transform on top of MCA's child scaling: the head shrinks and drops
 * to the belly. Clearing the flag before those layers copy it keeps their parts where MCA drew the body.
 */
public class AdultProportionsLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {

    public AdultProportionsLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, T entity,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        getParentModel().young = false;
    }
}
