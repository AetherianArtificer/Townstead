package com.aetherianartificer.townstead.mixin.compat.mca;

import com.aetherianartificer.townstead.compat.curios.CuriosCompat;
import com.aetherianartificer.townstead.inventory.VillagerInventoryMenu;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.interaction.EntityCommandHandler;
import net.conczin.mca.entity.interaction.VillagerCommandHandler;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Routes MCA's "inventory" interaction button to Townstead's villager inventory screen (portrait,
 * equipment, Curios column, storage) instead of the plain chest. Only when Curios is installed; without
 * it MCA's chest opens as before. Extends the handler's superclass so the handled villager is reachable
 * as the inherited {@code entity} field on both loaders.
 */
@Mixin(VillagerCommandHandler.class)
public abstract class VillagerCommandHandlerInventoryMixin extends EntityCommandHandler<VillagerEntityMCA> {

    protected VillagerCommandHandlerInventoryMixin(VillagerEntityMCA entity) {
        super(entity);
    }

    @Inject(method = "handle", at = @At("HEAD"), cancellable = true, remap = false)
    private void townstead$openVillagerInventory(ServerPlayer player, String command, CallbackInfoReturnable<Boolean> cir) {
        if (!"inventory".equals(command) || !CuriosCompat.present()) return;
        VillagerInventoryMenu.open(player, this.entity);
        cir.setReturnValue(false);
    }
}
