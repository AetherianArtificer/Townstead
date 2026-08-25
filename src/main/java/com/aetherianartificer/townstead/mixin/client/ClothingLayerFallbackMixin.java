package com.aetherianartificer.townstead.mixin.client;

import com.aetherianartificer.townstead.profession.ClientProfessionClothing;
import net.conczin.mca.client.model.CommonVillagerModel;
import net.conczin.mca.client.render.layer.ClothingLayer;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Applies per-client clothing fallbacks before MCA resolves its clothing texture. */
@Mixin(ClothingLayer.class)
public abstract class ClothingLayerFallbackMixin<T extends LivingEntity, M extends HumanoidModel<T>> {

    @Inject(method = "getSkin", remap = false, at = @At("HEAD"), require = 1)
    private void townstead$avoidMissingClothingTexture(T entity,
            CallbackInfoReturnable<ResourceLocation> cir) {
        if (CommonVillagerModel.getVillager(entity) instanceof VillagerEntityMCA villager) {
            ClientProfessionClothing.ensureRenderable(villager);
        }
    }
}
