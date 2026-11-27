package com.aetherianartificer.townstead.mixin.compat.cybernetics;

import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
//? if neoforge {
import com.aetherianartificer.townstead.compat.cybernetics.FaceplateDisguiseClient;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//?}

/** Marks which entity is rendering, so a disguised player draws as the player their alias names. */
@Mixin(EntityRenderDispatcher.class)
public abstract class FaceplateDisguiseRenderMixin {

    //? if neoforge {
    @Inject(method = "render", at = @At("HEAD"))
    private void townstead$beginDisguise(Entity entity, double x, double y, double z, float yaw, float partialTick,
                                         PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        FaceplateDisguiseClient.beginRender(entity);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void townstead$endDisguise(Entity entity, double x, double y, double z, float yaw, float partialTick,
                                       PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        FaceplateDisguiseClient.endRender();
    }
    //?}
}
