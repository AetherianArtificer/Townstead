package com.aetherianartificer.townstead.mixin.client;

import com.aetherianartificer.townstead.client.species.RigModels;
import com.aetherianartificer.townstead.client.species.SpeciesRigLayer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.client.model.VillagerEntityModelMCA;
import net.conczin.mca.client.render.VillagerLikeEntityMCARenderer;
import net.conczin.mca.entity.VillagerLike;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds the {@link SpeciesRigLayer} to MCA's villager renderer, lazily on the first {@code scale}
 * call (an MCA method, name-stable on both branches) like the attachment layer, so a villager with
 * an alternate species rig renders that model.
 */
@Mixin(VillagerLikeEntityMCARenderer.class)
public abstract class VillagerSpeciesRigLayerMixin<T extends Mob & VillagerLike<T>>
        extends LivingEntityRenderer<T, VillagerEntityModelMCA<T>> {

    @Unique
    private boolean townstead$rigLayerAdded;

    protected VillagerSpeciesRigLayerMixin(EntityRendererProvider.Context ctx,
                                           VillagerEntityModelMCA<T> model, float shadowRadius) {
        super(ctx, model, shadowRadius);
    }

    @Inject(method = "scale", at = @At("HEAD"), remap = false, require = 1)
    private void townstead$addRigLayer(Mob villager, PoseStack matrices, float tickDelta, CallbackInfo ci) {
        if (townstead$rigLayerAdded) return;
        townstead$rigLayerAdded = true;
        this.addLayer(new SpeciesRigLayer<>(this, RigModels.VILLAGER_HOST_BASELINE));
    }
}
