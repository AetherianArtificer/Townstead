package com.aetherianartificer.townstead.mixin.compat.mca;

import com.aetherianartificer.townstead.villager.NonResidents;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.interaction.EntityCommandHandler;
import net.conczin.mca.entity.interaction.VillagerCommandHandler;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * MCA's "stay_in_village" is made for adventurers and takes their profession away. A newcomer who
 * agrees to stay keeps theirs and simply stops being a newcomer.
 */
@Mixin(VillagerCommandHandler.class)
public abstract class VillagerCommandHandlerNewcomerStayMixin extends EntityCommandHandler<VillagerEntityMCA> {

    protected VillagerCommandHandlerNewcomerStayMixin(VillagerEntityMCA entity) {
        super(entity);
    }

    @Inject(method = "handle", at = @At("HEAD"), cancellable = true, remap = false)
    private void townstead$newcomerStays(ServerPlayer player, String command, CallbackInfoReturnable<Boolean> cir) {
        if (!"stay_in_village".equals(command) || !NonResidents.isNewcomer(this.entity)) return;
        NonResidents.settle(this.entity);
        cir.setReturnValue(true);
    }
}
