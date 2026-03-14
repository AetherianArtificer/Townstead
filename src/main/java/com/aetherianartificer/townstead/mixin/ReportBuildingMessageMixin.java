package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.upgrade.BuildingTierReconciler;
import net.conczin.mca.network.c2s.ReportBuildingMessage;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
//? if <1.21 {
/*import org.spongepowered.asm.mixin.Shadow;
*///?}
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ReportBuildingMessage.class)
public abstract class ReportBuildingMessageMixin {
    //? if <1.21 {
    /*@Shadow(remap = false)
    private ReportBuildingMessage.Action action;
    *///?}

    //? if >=1.21 {
    @Inject(method = "handleServer", at = @At("TAIL"), remap = false)
    //?} else {
    /*@Inject(method = "receive", at = @At("TAIL"), remap = false)
    *///?}
    private void townstead$reconcileTieredBuildingsAfterBuildingAction(ServerPlayer player, CallbackInfo ci) {
        //? if >=1.21 {
        ReportBuildingMessage self = (ReportBuildingMessage) (Object) this;
        ReportBuildingMessage.Action act = self.action();
        //?} else {
        /*ReportBuildingMessage.Action act = this.action;
        *///?}
        switch (act) {
            case ADD, ADD_ROOM, REMOVE, FULL_SCAN ->
                    VillageManager.get(player.serverLevel())
                            .findNearestVillage(player)
                            .ifPresent(v -> BuildingTierReconciler.reconcileVillage(v, player.serverLevel()));
            default -> {
            }
        }
    }
}
