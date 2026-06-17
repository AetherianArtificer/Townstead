package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.origin.ability.Phasing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies {@code phasing} (noclip) around the physics tick: HEAD toggles
 * {@code noPhysics} before {@code aiStep} runs {@code travel}/{@code move}, TAIL hands
 * the local player vertical flight (gravity would drop a noclipping player through the
 * floor). Runs both sides of {@code aiStep}; {@code Phasing} resolves state per side so
 * client prediction matches the server. Parity: same hook both branches (SRG on Forge).
 */
@Mixin(net.minecraft.world.entity.LivingEntity.class)
public abstract class LivingEntityPhasingMixin {

    //? if neoforge {
    @Inject(method = "aiStep", at = @At("HEAD"))
    //?} else {
    /*@Inject(method = "m_8107_", at = @At("HEAD"), remap = false)
    *///?}
    private void townstead$phasingPre(CallbackInfo ci) {
        Phasing.preTick((net.minecraft.world.entity.LivingEntity) (Object) this);
    }

    //? if neoforge {
    @Inject(method = "aiStep", at = @At("TAIL"))
    //?} else {
    /*@Inject(method = "m_8107_", at = @At("TAIL"), remap = false)
    *///?}
    private void townstead$phasingPost(CallbackInfo ci) {
        Phasing.postTick((net.minecraft.world.entity.LivingEntity) (Object) this);
    }
}
