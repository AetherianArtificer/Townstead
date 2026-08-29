package com.aetherianartificer.townstead.mixin.client;

import com.aetherianartificer.townstead.profession.ProfessionClothing;
import com.aetherianartificer.townstead.profession.def.ClothingChoice;
import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.client.model.CommonVillagerModel;
import net.conczin.mca.client.render.layer.HairLayer;
import net.conczin.mca.client.render.layer.VillagerLayer;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies an outfit's opt-in hair coverage after MCA has copied parent-model visibility. */
@Mixin(HairLayer.class)
public abstract class HairLayerCoveredClothingMixin<T extends LivingEntity, M extends HumanoidModel<T>>
        extends VillagerLayer<T, M> {

    protected HairLayerCoveredClothingMixin(RenderLayerParent<T, M> parent, M model) {
        super(parent, model);
    }

    @Inject(method = "renderFinal", remap = false, at = @At("HEAD"), require = 1)
    private void townstead$applyClothingHairCoverage(PoseStack poseStack,
                                                      MultiBufferSource buffers,
                                                      int packedLight,
                                                      T entity,
                                                      float partialTick,
                                                      boolean visible,
                                                      boolean glowing,
                                                      CallbackInfo ci) {
        if (!(CommonVillagerModel.getVillager(entity) instanceof VillagerEntityMCA villager)) return;
        ClothingChoice.HairPolicy policy = ProfessionClothing.hairPolicy(villager);
        if (policy == ClothingChoice.HairPolicy.HIDDEN) {
            this.model.setAllVisible(false);
        } else if (policy == ClothingChoice.HairPolicy.COVERED) {
            // MCA renders head hair on both a close-fitting cube and a larger outer shell.
            // A complete hat must suppress both, while body and arm parts remain available
            // for hairstyles that hang below the headwear.
            this.model.head.visible = false;
            this.model.hat.visible = false;
        }
    }
}
