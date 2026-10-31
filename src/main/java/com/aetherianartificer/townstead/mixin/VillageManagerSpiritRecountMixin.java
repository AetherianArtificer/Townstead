package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.mixin.accessor.VillageManagerAccessor;
import com.aetherianartificer.townstead.spirit.SpiritReconciler;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * MCA adds graveyards and other grouped sites on its own, from villagers' reports, without a
 * player report. Spirit is otherwise only recounted on player reports, so a graveyard that
 * reached its tombstone count never showed up. Recount when this pass changed the village's
 * spirit buildings.
 */
@Mixin(VillageManager.class)
public abstract class VillageManagerSpiritRecountMixin {
    @Unique private int townstead$spiritVillageId = Integer.MIN_VALUE;
    @Unique private long townstead$spiritPrint;

    @Inject(method = "processBuilding(Lnet/minecraft/core/BlockPos;)Lnet/conczin/mca/server/world/data/Building$validationResult;",
            at = @At("HEAD"), remap = false, require = 0)
    private void townstead$captureSpiritPrint(BlockPos pos, CallbackInfoReturnable<Building.validationResult> cir) {
        Village village = ((VillageManager) (Object) this).findNearestVillage(pos, Village.MERGE_MARGIN).orElse(null);
        townstead$spiritVillageId = village == null ? Integer.MIN_VALUE : village.getId();
        townstead$spiritPrint = SpiritReconciler.contributionPrint(village);
    }

    @Inject(method = "processBuilding(Lnet/minecraft/core/BlockPos;)Lnet/conczin/mca/server/world/data/Building$validationResult;",
            at = @At("RETURN"), remap = false, require = 0)
    private void townstead$recountSpirit(BlockPos pos, CallbackInfoReturnable<Building.validationResult> cir) {
        if (cir.getReturnValue() != Building.validationResult.SUCCESS) return;
        Village village = ((VillageManager) (Object) this).findNearestVillage(pos, Village.MERGE_MARGIN).orElse(null);
        if (village == null) return;
        if (village.getId() == townstead$spiritVillageId
                && SpiritReconciler.contributionPrint(village) == townstead$spiritPrint) return;
        ServerLevel level = ((VillageManagerAccessor) (Object) this).townstead$world();
        SpiritReconciler.reconcileVillage(level, village);
    }
}
