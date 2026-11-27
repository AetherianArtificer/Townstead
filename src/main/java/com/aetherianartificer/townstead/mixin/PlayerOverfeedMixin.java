package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.root.Overfeed;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A bearer of {@code pheno:overfeed} can keep eating past full. Parity: SRG name on Forge. */
@Mixin(Player.class)
public abstract class PlayerOverfeedMixin {

    //? if neoforge {
    @Inject(method = "canEat", at = @At("RETURN"), cancellable = true)
    //?} else {
    /*@Inject(method = "m_36391_", at = @At("RETURN"), cancellable = true, remap = false)
    *///?}
    private void townstead$eatPastFull(boolean canAlwaysEat, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue() && Overfeed.canEatPastFull((Player) (Object) this)) cir.setReturnValue(true);
    }
}
