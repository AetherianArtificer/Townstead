package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.compat.mca.McaBuildings;
import com.aetherianartificer.townstead.mixin.accessor.VillageManagerAccessor;
import com.aetherianartificer.townstead.recognition.CaveRooms;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.BuildingScanResult;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Keeps the villagers' auto-scan from registering a cavern as a room, before the floor system
 * (gated in TownsteadMixinPlugin). Only the auto-scan: a room scanned by hand never takes this
 * path, and a Full Scan's rescan is left alone because refusing there would delete the room.
 */
@Mixin(VillageManager.class)
public abstract class VillageManagerCaveRoomMixin {
    @WrapOperation(method = "processBuilding(Lnet/minecraft/core/BlockPos;ZZLjava/lang/String;)Lnet/conczin/mca/server/world/data/Building$validationResult;",
            at = @At(value = "INVOKE", target = "Lnet/conczin/mca/server/world/data/VillageManager;analyzeBuilding(Lnet/minecraft/core/BlockPos;Z)Lnet/conczin/mca/server/world/data/BuildingScanResult;"),
            remap = false, require = 0)
    private BuildingScanResult townstead$refuseCaverns(VillageManager manager, BlockPos pos, boolean strictScan,
                                                       Operation<BuildingScanResult> original,
                                                       @Local(argsOnly = true, ordinal = 0) boolean enforce) {
        BuildingScanResult scan = original.call(manager, pos, strictScan);
        if (enforce || !strictScan || scan.result() != Building.validationResult.SUCCESS) return scan;
        ServerLevel level = ((VillageManagerAccessor) (Object) this).townstead$world();
        Building room = scan.building();
        if (level == null || !CaveRooms.isCavern(level, room.getPos0(), room.getPos1(), McaBuildings.size(room))) {
            return scan;
        }
        //? if >=1.21 {
        return scan;
        //?} else {
        /*return new BuildingScanResult(Building.validationResult.SIZE_LIMIT, scan.source(), scan.strictScan(),
                room, java.util.List.of(), scan.village());
        *///?}
    }
}
