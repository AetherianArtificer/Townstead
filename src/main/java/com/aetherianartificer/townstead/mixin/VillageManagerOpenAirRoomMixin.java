package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.mixin.accessor.VillageManagerAccessor;
import com.aetherianartificer.townstead.recognition.BuildingEnclosurePolicies;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Adding a room asks MCA which building already holds the spot, and an open-air site (a square, a
 * pen) holds everything inside its wide bounds. A spot under a roof there is a room of its own,
 * not part of the site, so the site does not claim it and the room is scanned as new.
 */
@Mixin(VillageManager.class)
public abstract class VillageManagerOpenAirRoomMixin {
    @WrapOperation(method = "getBlockedResult",
            at = @At(value = "INVOKE", target = "Lnet/conczin/mca/server/world/data/Building;containsPos(Lnet/minecraft/core/BlockPos;)Z"),
            remap = false, require = 0)
    private boolean townstead$roomInsideOpenAirSite(Building building, BlockPos pos, Operation<Boolean> original) {
        boolean contains = original.call(building, pos);
        if (!contains || !BuildingEnclosurePolicies.allowsOpenAir(building.getType())) return contains;
        var level = ((VillageManagerAccessor) (Object) this).townstead$world();
        return level == null || level.canSeeSky(pos.above());
    }
}
