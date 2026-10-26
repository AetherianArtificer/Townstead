package com.aetherianartificer.townstead.mixin.client;

import com.aetherianartificer.townstead.client.species.RigCamera;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Visual camera fallback for alternate player rigs without a declared physical hitbox. Vanilla keeps a humanoid's
 * 1.62 eye height regardless of the body, so a low spider-folk player floats a tall column above its own
 * model. {@link RigCamera} derives the eye height from the rig's {@code camera.bone}; we override the
 * cached {@code getEyeHeight()} (what the camera and eye position read) with it. Client-only: the bone
 * position lives in the baked model, so the server keeps its default eye height (only the camera moves).
 * Hitbox-declaring rigs and MCA villagers instead use the shared physical eye height.
 *
 * <p>1.20.1 Forge SRG: {@code m_20192_} getEyeHeight.</p>
 */
@Mixin(Entity.class)
public abstract class EntityRigEyeHeightMixin {

    //? if neoforge {
    @Inject(method = "getEyeHeight", at = @At("RETURN"), cancellable = true)
    //?} else {
    /*@Inject(method = "m_20192_", at = @At("RETURN"), cancellable = true, remap = false)
    *///?}
    private void townstead$rigEyeHeight(CallbackInfoReturnable<Float> cir) {
        Entity self = (Entity) (Object) this;
        if (!self.level().isClientSide) return;
        if (!(self instanceof Player) && !(self instanceof VillagerEntityMCA)) return;
        // A physical hitbox supplies the same eye height on client and server.
        if (self instanceof VillagerEntityMCA) return;
        var definition = com.aetherianartificer.townstead.root.rig.RigHitboxes.definition((LivingEntity) self);
        if (definition != null && definition.hitbox() != null) return;
        Float eye = RigCamera.eyeHeight((LivingEntity) self);
        if (eye != null) cir.setReturnValue(eye);
    }
}
