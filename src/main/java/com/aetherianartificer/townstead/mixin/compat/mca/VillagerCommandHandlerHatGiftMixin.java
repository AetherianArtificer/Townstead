package com.aetherianartificer.townstead.mixin.compat.mca;

import com.aetherianartificer.townstead.compat.hats.HatsCompat;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.interaction.EntityCommandHandler;
import net.conczin.mca.entity.interaction.VillagerCommandHandler;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Routes the "give_hat" interaction button to the Hats Renewed bridge: the hat the player wears
 * goes to the villager, who keeps it. Without Hats the button is hidden client-side and the
 * command answers with a message rather than silence.
 */
@Mixin(VillagerCommandHandler.class)
public abstract class VillagerCommandHandlerHatGiftMixin extends EntityCommandHandler<VillagerEntityMCA> {

    protected VillagerCommandHandlerHatGiftMixin(VillagerEntityMCA entity) {
        super(entity);
    }

    @Inject(method = "handle", at = @At("HEAD"), cancellable = true, remap = false)
    private void townstead$giveHat(ServerPlayer player, String command, CallbackInfoReturnable<Boolean> cir) {
        if (!"give_hat".equals(command)) return;
        HatsCompat.Gift result = HatsCompat.present() ? HatsCompat.give(player, this.entity) : HatsCompat.Gift.UNAVAILABLE;
        player.displayClientMessage(HatsCompat.message(result), true);
        cir.setReturnValue(false);
    }
}
