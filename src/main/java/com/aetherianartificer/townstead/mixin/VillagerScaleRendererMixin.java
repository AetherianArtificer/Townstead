package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.origin.LifeStageScale;
import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.client.render.VillagerLikeEntityMCARenderer;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies the villager's interpolated life-stage size on top of MCA's model
 * scale, so the rendered body keeps growing/shrinking through adulthood and
 * senior instead of plateauing at MCA's adult cap. Hooks the renderer's vanilla
 * {@code scale} override (matched by both official and SRG names); the extra
 * uniform scale composes with MCA's. No-op (1.0) without synced life data, and
 * driven by the editor preview override while the Age slider is dragged.
 */
@Mixin(VillagerLikeEntityMCARenderer.class)
public abstract class VillagerScaleRendererMixin {

    // MCA's override narrows the param to Mob, so it keeps the name `scale` on both
    // branches (only the synthetic LivingEntity bridge becomes m_7546_ on Forge).
    @Inject(method = "scale", at = @At("HEAD"), remap = false, require = 1)
    private void townstead$applyStageScale(Mob villager, PoseStack matrices, float tickDelta, CallbackInfo ci) {
        float f = LifeStageScale.forVillager(villager);
        if (f != 1f) matrices.scale(f, f, f);
    }
}
