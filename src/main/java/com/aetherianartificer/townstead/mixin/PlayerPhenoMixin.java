package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.origin.hook.PhenoHooks;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Pheno interception points on {@link Player}. The {@code exhaustion} modifier scales food
 * exhaustion (e.g. a big-appetite race draining hunger faster); there is no exhaustion event, so
 * this rescales the argument to {@code Player.causeFoodExhaustion} ({@code m_36399_} on 1.20.1).
 * Player-only; MCA villager hunger runs through Townstead's own system. All logic is in
 * {@link PhenoHooks}.
 */
@Mixin(Player.class)
public abstract class PlayerPhenoMixin {

    //? if neoforge {
    @ModifyVariable(method = "causeFoodExhaustion", at = @At("HEAD"), argsOnly = true)
    //?} else {
    /*@ModifyVariable(method = "m_36399_", at = @At("HEAD"), argsOnly = true, remap = false)
    *///?}
    private float townstead$scaleExhaustion(float exhaustion) {
        return PhenoHooks.exhaustion((Player) (Object) this, exhaustion);
    }
}
