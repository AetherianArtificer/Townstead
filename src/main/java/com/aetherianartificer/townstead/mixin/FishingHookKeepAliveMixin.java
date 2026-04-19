package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.hunger.FishermanHookLinkStore;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.projectile.FishingHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if neoforge {
import org.spongepowered.asm.mixin.injection.Redirect;
//?}

/**
 * Keep FishingHook entities alive on the client even when getPlayerOwner()
 * returns null. Our fishermen spawn hooks owned by a per-villager FakePlayer
 * that clients never see, so vanilla's two null-owner bailouts fire:
 *
 *   - FishingHook.recreateFromPacket: calls kill() on spawn if owner id
 *     doesn't resolve to a Player client-side. Produces the
 *     "Failed to recreate fishing hook on client" error log and the
 *     entity is removed instantly — before any renderer can draw it.
 *
 *   - FishingHook.tick: calls discard() every tick when getPlayerOwner()
 *     is null. Would un-remove-proof any surviving hook.
 *
 * This mixin redirects both calls to a no-op on the client. Server-side
 * behavior is preserved (the FakePlayer is a real ServerPlayer so null
 * owners server-side remain legitimately orphaned and still despawn).
 *
 * 1.20.1 Forge has no mixin refmap so the INVOKE target descriptors won't
 * remap from mojmap. Scoped to NeoForge 1.21.1 via stonecutter; the
 * annotations simply aren't compiled on 1.20.1 Forge and the hook stays
 * invisible there (same state as before this fix).
 */
@Mixin(FishingHook.class)
public abstract class FishingHookKeepAliveMixin {

    @Unique private double townstead$lerpX;
    @Unique private double townstead$lerpY;
    @Unique private double townstead$lerpZ;
    @Unique private float townstead$lerpYaw;
    @Unique private float townstead$lerpPitch;
    @Unique private int townstead$lerpSteps;

    /**
     * Vanilla FishingHook.lerpTo is intentionally empty — a real player-owned
     * hook relies on its own client-side tick() physics to update position.
     * For our FakePlayer-owned hooks the client tick() skips that branch
     * entirely (null owner hits the discard path we already redirect), so
     * position would never update. We implement vanilla's standard lerp:
     * stash the target + step count, and tickClientLerp below eases the
     * client hook toward it one step per tick. This also preserves the
     * xo/yo/zo → x/y/z relationship the renderer relies on for smooth
     * partialTick interpolation between frames.
     */
    //? if neoforge {
    @Inject(method = "lerpTo", at = @At("HEAD"), cancellable = true)
    //?} else {
    /*@Inject(method = "lerpTo", remap = false, require = 0, at = @At("HEAD"), cancellable = true)
    *///?}
    private void townstead$lerpToForLinkedHooks(
            double x, double y, double z, float yaw, float pitch, int lerpSteps,
            CallbackInfo ci
    ) {
        try {
            FishingHook self = (FishingHook) (Object) this;
            if (!self.level().isClientSide) return;
            if (FishermanHookLinkStore.villagerFor(self.getId()) == null) return;
            townstead$lerpX = x;
            townstead$lerpY = y;
            townstead$lerpZ = z;
            townstead$lerpYaw = yaw;
            townstead$lerpPitch = pitch;
            townstead$lerpSteps = Math.max(1, lerpSteps);
            ci.cancel();
        } catch (Throwable t) {
            // Defensive: mixin exceptions at packet-processing time can
            // cascade. Fall back to vanilla no-op on any failure.
        }
    }

    /**
     * Per-tick lerp step. Runs before the rest of FishingHook.tick() so our
     * xo/yo/zo always equal last frame's position (what Mth.lerp in the
     * renderer needs) and x/y/z ease toward the stored server target.
     */
    //? if neoforge {
    @Inject(method = "tick", at = @At("HEAD"))
    //?} else {
    /*@Inject(method = "tick", remap = false, require = 0, at = @At("HEAD"))
    *///?}
    private void townstead$tickClientLerp(CallbackInfo ci) {
        try {
            FishingHook self = (FishingHook) (Object) this;
            if (!self.level().isClientSide) return;
            if (FishermanHookLinkStore.villagerFor(self.getId()) == null) return;

            // Save last-frame position for renderer partialTick interp.
            self.xo = self.getX();
            self.yo = self.getY();
            self.zo = self.getZ();
            self.yRotO = self.getYRot();
            self.xRotO = self.getXRot();

            if (townstead$lerpSteps <= 0) return;

            double step = 1.0 / (double) townstead$lerpSteps;
            double nx = Mth.lerp(step, self.getX(), townstead$lerpX);
            double ny = Mth.lerp(step, self.getY(), townstead$lerpY);
            double nz = Mth.lerp(step, self.getZ(), townstead$lerpZ);
            float nyaw = self.getYRot() + (float) Mth.wrapDegrees(townstead$lerpYaw - self.getYRot()) * (float) step;
            float npitch = self.getXRot() + (float) (townstead$lerpPitch - self.getXRot()) * (float) step;
            self.setPos(nx, ny, nz);
            self.setYRot(nyaw);
            self.setXRot(npitch);
            townstead$lerpSteps--;
        } catch (Throwable t) {
            // Swallow to avoid breaking the tick chain.
        }
    }

    //? if neoforge {
    @Redirect(
            method = "recreateFromPacket",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/projectile/FishingHook;kill()V")
    )
    private void townstead$suppressClientKillOnSpawn(FishingHook self) {
        // no-op: we'll render the bobber ourselves via FishingHookRendererMixin
        // and fish-line rendering via FishermanLineRenderer.
    }

    @Redirect(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/projectile/FishingHook;discard()V",
                    ordinal = 0
            )
    )
    private void townstead$suppressClientDiscardOnNullOwner(FishingHook self) {
        // The first discard() call in tick() corresponds to the
        // "if (player == null) discard();" path. Preserve it on the server
        // where a legitimately orphaned hook should clean itself up, but
        // on the client leave it alive — our FakePlayer-owned hooks are
        // always "null-owner" client-side and would otherwise vanish each
        // tick right after the server spawns them.
        if (!self.level().isClientSide) {
            self.discard();
            return;
        }
        if (FishermanHookLinkStore.villagerFor(self.getId()) == null) {
            // Not one of ours — still a foreign orphan, let vanilla clean up.
            self.discard();
        }
    }
    //?}
}
