package com.aetherianartificer.townstead.mixin.client;

import com.aetherianartificer.townstead.client.species.HumanoidEyes;
import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.client.render.layer.FaceLayer;
import net.conczin.mca.client.render.layer.VillagerLayer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hands the face over to a root's own eyes when it has an {@code eyes} gene: MCA's eyes, blink, and
 * iris tinting are skipped and {@link HumanoidEyes} draws the bearer's eye strip on the face
 * layer's own model — MCA's face shell, already head-only and posed by the time
 * {@code renderFinal} runs, so the swap needs no second model or layer. Pass-through for everyone
 * else, so ordinary villagers keep MCA's face entirely.
 *
 * <p>The target class differs by branch: 1.21.1's {@code FaceLayer} overrides {@code renderFinal}
 * (and never calls super), while 7.6.15's inherits it, where an injector on the subclass would have
 * no target at all — so that branch hooks {@code VillagerLayer} and filters to face layers.
 * {@code renderFinal} is MCA's own method either way, hence {@code remap=false}. The model field
 * lives on the superclass, reached by cast rather than a {@code @Shadow} that wouldn't traverse the
 * hierarchy.</p>
 */
//? if neoforge {
@Mixin(FaceLayer.class)
//?} else {
/*@Mixin(VillagerLayer.class)
*///?}
public abstract class FaceLayerCustomEyesMixin<T extends LivingEntity, M extends HumanoidModel<T>> {

    @Inject(method = "renderFinal", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void townstead$customEyes(PoseStack transform, MultiBufferSource provider, int light, T villager,
                                      float tickDelta, boolean visible, boolean glowing, CallbackInfo ci) {
        @SuppressWarnings("unchecked")
        VillagerLayer<T, M> self = (VillagerLayer<T, M>) (Object) this;
        //? if neoforge {
        // The face layer IS the target on this branch.
        //?} else {
        /*if (!(self instanceof FaceLayer)) return;
        *///?}
        if (HumanoidEyes.render(self.model, transform, provider, light, villager, visible, glowing)) {
            ci.cancel();
        }
    }
}
