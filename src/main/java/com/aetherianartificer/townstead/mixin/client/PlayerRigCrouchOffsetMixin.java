package com.aetherianartificer.townstead.mixin.client;

import com.aetherianartificer.townstead.client.species.RigModels;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Generic rigs crouch through their own bones; vanilla's human-origin drop would bury their feet. */
@Mixin(PlayerRenderer.class)
public abstract class PlayerRigCrouchOffsetMixin {
    //? if neoforge {
    @Inject(method = "getRenderOffset(Lnet/minecraft/client/player/AbstractClientPlayer;F)Lnet/minecraft/world/phys/Vec3;",
            at = @At("RETURN"), cancellable = true, require = 1)
    //?} else {
    /*@Inject(method = "m_7860_(Lnet/minecraft/client/player/AbstractClientPlayer;F)Lnet/minecraft/world/phys/Vec3;",
            remap = false, at = @At("RETURN"), cancellable = true, require = 1)
    *///?}
    private void townstead$keepRigFeetGrounded(AbstractClientPlayer player, float partial,
                                               CallbackInfoReturnable<Vec3> cir) {
        if (!player.isCrouching() || !RigModels.isGeneric(RigModels.rigBaseFor(player))) return;
        float scale = 1F;
        //? if neoforge {
        scale = player.getScale();
        //?}
        cir.setReturnValue(cir.getReturnValue().add(0, scale * 2.0 / 16.0, 0));
    }
}
