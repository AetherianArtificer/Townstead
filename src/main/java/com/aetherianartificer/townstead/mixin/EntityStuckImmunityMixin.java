package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.origin.StuckImmunity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A race with the {@code stuck_immunity} gene isn't slowed by the blocks it lists (a spider walking
 * through cobwebs). {@code makeStuckInBlock} is the single chokepoint cobwebs and sweet berry bushes use
 * to apply their drag, so skipping it at HEAD leaves the entity's motion untouched. The check is
 * side-aware ({@link StuckImmunity}): the server reads authoritative genes (villager movement), the
 * controlling client reads the synced set so a spider-folk player predicts the same and doesn't stick.
 * The gene lookup is only paid while actually inside such a block.
 */
@Mixin(Entity.class)
public abstract class EntityStuckImmunityMixin {

    //? if neoforge {
    @Inject(method = "makeStuckInBlock", at = @At("HEAD"), cancellable = true)
    //?} else {
    /*@Inject(method = "m_7601_", at = @At("HEAD"), cancellable = true, remap = false)
    *///?}
    private void townstead$skipStuckForImmune(BlockState state, Vec3 motionMultiplier, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (self instanceof LivingEntity living && StuckImmunity.covers(living, state)) {
            ci.cancel();
        }
    }
}
