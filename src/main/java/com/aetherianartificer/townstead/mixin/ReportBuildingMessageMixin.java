package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.upgrade.BuildingTierReconciler;
import net.conczin.mca.network.c2s.ReportBuildingMessage;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ReportBuildingMessage.class)
public abstract class ReportBuildingMessageMixin {
    @Inject(method = "handleServer", at = @At("TAIL"))
    private void townstead$reconcileTieredBuildingsAfterBuildingAction(ServerPlayer player, CallbackInfo ci) {
        ReportBuildingMessage self = (ReportBuildingMessage) (Object) this;
        switch (self.action()) {
            case ADD, ADD_ROOM, REMOVE, FORCE_TYPE, FULL_SCAN ->
                    VillageManager.get(player.serverLevel())
                            .findNearestVillage(player)
                            .ifPresent(BuildingTierReconciler::reconcileVillage);
            default -> {
            }
        }
    }
}

