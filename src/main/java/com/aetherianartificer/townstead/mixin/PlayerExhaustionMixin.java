package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.origin.gene.types.ModifierGeneType;
import com.aetherianartificer.townstead.origin.modifier.GeneModifiers;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Scales the player's food exhaustion by any {@code exhaustion} modifier genes (Origins'
 * {@code modify_exhaustion}, e.g. a big-appetite race draining hunger faster). There's no
 * event for exhaustion, so this rescales the argument to {@code Player.causeFoodExhaustion}
 * ({@code m_36399_} on the 1.20.1 Forge build). Player-only; MCA villager hunger runs
 * through Townstead's own system, not this path.
 */
@Mixin(Player.class)
public abstract class PlayerExhaustionMixin {

    //? if neoforge {
    @ModifyVariable(method = "causeFoodExhaustion", at = @At("HEAD"), argsOnly = true)
    //?} else {
    /*@ModifyVariable(method = "m_36399_", at = @At("HEAD"), argsOnly = true, remap = false)
    *///?}
    private float townstead$scaleExhaustion(float exhaustion) {
        return GeneModifiers.modify((Player) (Object) this, ModifierGeneType.Modifier.EXHAUSTION, exhaustion);
    }
}
