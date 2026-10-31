package com.aetherianartificer.townstead.mixin.compat.vampirism;

import com.aetherianartificer.townstead.compat.vampirism.PlayerBites;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Marks a Vampirism player's bite while it runs, so the bitten villager knows who bit them. */
@Pseudo
@Mixin(targets = "de.teamlapen.vampirism.entity.player.vampire.VampirePlayer", remap = false)
public abstract class VampirePlayerBiteMixin {
    @Inject(method = "biteFeed", at = @At("HEAD"), require = 0)
    private void townstead$beginBite(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!entity.level().isClientSide) PlayerBites.begin(this);
    }

    @Inject(method = "biteFeed", at = @At("RETURN"), require = 0)
    private void townstead$endBite(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!entity.level().isClientSide) PlayerBites.end(entity);
    }
}
