package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.origin.ability.Ability;
import com.aetherianartificer.townstead.origin.ability.MovementAbilities;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Spider-style wall climbing for a {@code climbing} entity, driven through vanilla's own per-tick
 * {@link LivingEntity#onClimbable()} (the once-per-10-ticks velocity nudge it replaced could not beat
 * gravity). Control scheme, for a player:
 * <ul>
 *   <li>push into a wall to climb up (vanilla's {@code horizontalCollision || jumping} climb boost);</li>
 *   <li>stop, and stick in place rather than slide off (slide suppressed below);</li>
 *   <li>sneak to ease down the wall;</li>
 *   <li>jump to let go and fall.</li>
 * </ul>
 *
 * <p>Resolved per side by {@link MovementAbilities} so the owning client predicts what the server does.
 * The cling and sneak/jump controls are player-only: an AI villager keeps the basic push-to-climb so it
 * never freezes latched to a wall mid-path. We only ever turn a {@code false} into {@code true}
 * (ladders/vines already return true), so nothing existing is overridden. 1.20.1 Forge SRG: {@code
 * m_6147_} onClimbable, {@code m_5791_} isSuppressingSlidingDownLadder, {@code f_20899_} jumping.</p>
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityClimbMixin {

    //? if neoforge {
    @Shadow protected boolean jumping;
    //?} else {
    /*@Shadow(remap = false) protected boolean f_20899_;
    *///?}

    /** Armed once the player pushes into a wall; keeps them stuck until they jump or leave the wall. */
    private boolean townstead$clinging;

    /** Whether this entity is holding jump, the release control. */
    private boolean townstead$holdingJump() {
        //? if neoforge {
        return this.jumping;
        //?} else {
        /*return this.f_20899_;
        *///?}
    }

    //? if neoforge {
    @Inject(method = "onClimbable", at = @At("RETURN"), cancellable = true)
    //?} else {
    /*@Inject(method = "m_6147_", at = @At("RETURN"), cancellable = true, remap = false)
    *///?}
    private void townstead$abilityClimb(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) return;
        LivingEntity self = (LivingEntity) (Object) this;
        // The wall-frame movement controller owns the local player's climbing; while it drives (and a short
        // dismount grace after) don't add vanilla wall-climb, so it can't fight the controller at the wall
        // base. Real ladders already returned above, so this only suppresses our own push-to-climb.
        if (self.level().isClientSide
                && com.aetherianartificer.townstead.client.species.ClimbMove.isSuppressing(self)) return;
        boolean grounded = self.onGround();
        // Back on the ground: disarm the cling so it can't carry over to the next jump.
        if (grounded && townstead$clinging) townstead$clinging = false;
        // Cheap pre-filter: only a wall-pressed or airborne entity can be climbing; skip the common
        // grounded-and-clear case before the ability lookup so this stays off the general movement path.
        if (!self.horizontalCollision && grounded) return;
        if (!MovementAbilities.isActive(self, Ability.CLIMBING)) return;

        // Intent to climb = pushing into a wall the entity FACES, not just brushing walls while squeezing
        // through a narrow hole/doorway (where the collisions are on the sides, not ahead).
        boolean pushingIntoWall = self.horizontalCollision && townstead$facingWall(self);

        // Villagers keep the basic push-to-climb (no cling) so they never freeze latched to a wall mid-path.
        if (!(self instanceof Player)) {
            if (pushingIntoWall) cir.setReturnValue(true);
            return;
        }
        // Jump or sneak = deliberate release: let go of the wall and fall (sneak drops the player straight to
        // the ground instead of the vanilla slow crouch-slide).
        if (townstead$holdingJump() || self.isShiftKeyDown()) {
            townstead$clinging = false;
            return;
        }
        boolean beside = townstead$wallBeside(self);
        if (!beside) townstead$clinging = false;
        if (pushingIntoWall) {
            // Pushing into a wall it faces: this is the intent signal, so arm the cling and climb up.
            townstead$clinging = true;
            cir.setReturnValue(true);
        } else if (!grounded && beside && townstead$clinging) {
            // Armed earlier by a push and still beside the wall: stick in place instead of sliding off.
            // Brushing past or jumping near a wall without ever pushing never arms this, so it won't grab.
            cir.setReturnValue(true);
        }
    }

    //? if neoforge {
    @Inject(method = "isSuppressingSlidingDownLadder", at = @At("RETURN"), cancellable = true)
    //?} else {
    /*@Inject(method = "m_5791_", at = @At("RETURN"), cancellable = true, remap = false)
    *///?}
    private void townstead$stickToWall(CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!MovementAbilities.isActive(self, Ability.CLIMBING)) return;
        // Stick by default (suppress the slow slide); sneak to ease down. Vanilla only consults this for
        // players inside handleOnClimbable, so a villager climber is unaffected and keeps the basic climb.
        cir.setReturnValue(!self.isShiftKeyDown());
    }

    /** True when a solid block sits against the entity horizontally, so it has a wall to cling to. */
    private static boolean townstead$wallBeside(LivingEntity self) {
        AABB box = self.getBoundingBox().inflate(0.08, 0.0, 0.08);
        return !self.level().noCollision(self, box);
    }

    /** True when a solid block sits in the direction the entity faces (pushing into a wall, not past one). */
    private static boolean townstead$facingWall(LivingEntity self) {
        Direction d = self.getDirection();
        AABB box = self.getBoundingBox();
        return !self.level().noCollision(self, box.move(d.getStepX() * 0.1, 0.0, d.getStepZ() * 0.1));
    }
}
