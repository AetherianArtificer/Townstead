package com.aetherianartificer.townstead.mixin.accessor;

import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/** Mutable map access used only by Townstead's synthetic MCA building compatibility seam. */
@Mixin(value = Village.class, remap = false)
public interface VillageBuildingMapsAccessor {
    @Accessor("buildings")
    Map<Integer, Building> townstead$getBuildingMap();

    //? if >=1.21 {
    @Accessor("externalBuildings")
    Map<Integer, Building> townstead$getExternalBuildingMap();
    //?}
}
