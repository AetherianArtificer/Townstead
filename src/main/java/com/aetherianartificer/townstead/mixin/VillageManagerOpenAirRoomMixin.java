package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.compat.mca.McaBuildings;
import com.aetherianartificer.townstead.mixin.accessor.VillageManagerAccessor;
import com.aetherianartificer.townstead.recognition.OptionalBuildingRecognition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps MCA's room scan off Townstead's open-air sites, before the floor system (gated in
 * TownsteadMixinPlugin). MCA's Full Scan rescans every record from its middle with a room flood
 * fill: outdoors that fails and deletes whichever building holds the spot, and under a cave
 * ceiling it succeeds and turns the site into a room. Open-air sites are judged again by
 * {@link OptionalBuildingRecognition#recheckVillage} instead.
 */
@Mixin(VillageManager.class)
public abstract class VillageManagerOpenAirRoomMixin {
    @Inject(method = "processBuilding(Lnet/minecraft/core/BlockPos;ZZLjava/lang/String;)Lnet/conczin/mca/server/world/data/Building$validationResult;",
            at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void townstead$skipOpenAirRescan(BlockPos pos, boolean enforce, boolean strictScan, String forcedType,
                                             CallbackInfoReturnable<Building.validationResult> cir) {
        if (!enforce || strictScan || pos == null) return;
        ServerLevel level = ((VillageManagerAccessor) (Object) this).townstead$world();
        Village village = ((VillageManager) (Object) this).findNearestVillage(pos, Village.MERGE_MARGIN).orElse(null);
        if (level == null || village == null) return;
        for (Building building : McaBuildings.all(village)) {
            if (pos.equals(building.getCenter()) && OptionalBuildingRecognition.isOpenAirSite(level, village, building)) {
                cir.setReturnValue(Building.validationResult.SUCCESS);
                return;
            }
        }
    }

    /**
     * A failed rescan removes the first building that holds the spot. An open-air site holds it
     * only on its own ground and where no room does, so a stale room never takes the square or
     * pen around it down with it.
     */
    @WrapOperation(method = "getBlockedResult",
            at = @At(value = "INVOKE", target = "Lnet/conczin/mca/server/world/data/Building;containsPos(Lnet/minecraft/core/BlockPos;)Z"),
            remap = false, require = 0)
    private boolean townstead$openAirSiteGround(Building building, BlockPos pos, Operation<Boolean> original) {
        boolean contains = original.call(building, pos);
        if (!contains) return false;
        ServerLevel level = ((VillageManagerAccessor) (Object) this).townstead$world();
        Village village = level == null ? null
                : ((VillageManager) (Object) this).findNearestVillage(pos, Village.MERGE_MARGIN).orElse(null);
        if (village == null || !OptionalBuildingRecognition.isOpenAirSite(level, village, building)) return contains;
        if (!McaBuildings.contains(level, village, building, pos)) return false;
        for (Building other : McaBuildings.all(village)) {
            if (other == building || OptionalBuildingRecognition.isOpenAirSite(level, village, other)) continue;
            if (other.containsPos(pos)) return false;
        }
        return true;
    }
}
