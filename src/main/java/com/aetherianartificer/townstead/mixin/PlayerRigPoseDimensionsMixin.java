package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.root.rig.RigHitboxes;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes a rig's declared hitbox drive POSE SELECTION, not just collision.
 *
 * <p>{@link RigHitboxes} reaches the entity through {@code EntityEvent.Size}, which vanilla only fires
 * from {@code refreshDimensions} -- it replaces the CURRENT cached box. The per-pose query
 * ({@code Player.getDefaultDimensions} / 1.20.1 {@code Player.getDimensions}) is untouched, so
 * {@code updatePlayerPose} still measures every candidate pose with vanilla's 0.6x1.8 standing box.
 * A rig shorter than a block therefore "cannot stand" under a 1-block ceiling, and vanilla's last
 * resort is {@code Pose.SWIMMING} (crawl) -- whereupon {@code PlayerRenderer.setupRotations} pitches
 * the whole entity -90 degrees and a non-humanoid rig ends up on its back, face to the sky.</p>
 *
 * <p>Answering the per-pose query from the same rig hitbox lets a short rig simply stand in the gap it
 * actually fits in, so the crawl (and its pitch) never engages. Sleeping is excluded upstream by
 * {@code RigHitboxes} (null there), keeping vanilla's sleeping box.</p>
 *
 * <p>Runs on both sides: the pose is decided independently by the client and the server, so a
 * client-only hook would just be overwritten by the server's choice.</p>
 */
@Mixin(Player.class)
public abstract class PlayerRigPoseDimensionsMixin {

    //? if neoforge {
    @Inject(method = "getDefaultDimensions", at = @At("HEAD"), cancellable = true)
    //?} else {
    /*@Inject(method = "m_6972_", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    *///?}
    private void townstead$rigPoseDimensions(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
        EntityDimensions rig = RigHitboxes.dimensionsFor((Player) (Object) this, pose);
        if (rig != null) cir.setReturnValue(rig);
    }
}
