package com.aetherianartificer.townstead.work.station;

import com.aetherianartificer.townstead.work.recipe.StationType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StationsTest {

    @Test
    void emptyPlaceSurfaceIdentifiesAsTheBlockItWillPlace() {
        ResourceLocation air = ResourceLocation.tryParse("minecraft:air");
        ResourceLocation rawPizza = ResourceLocation.tryParse("pizzadelight:raw_pizza");

        assertEquals(rawPizza,
                Stations.slotBlockId(air, StationType.PLACE_SURFACE, rawPizza));
    }

    @Test
    void ordinaryStationKeepsItsWorldBlockIdentity() {
        ResourceLocation campfire = ResourceLocation.tryParse("minecraft:campfire");
        ResourceLocation rawPizza = ResourceLocation.tryParse("pizzadelight:raw_pizza");

        assertEquals(campfire,
                Stations.slotBlockId(campfire, StationType.FIRE_STATION, rawPizza));
    }

    @Test
    void rotatesNorthAuthoredStaffSideWithStationFacing() {
        Vec3i behind = new Vec3i(0, 0, 1);
        assertEquals(new Vec3i(0, 0, 1), Stations.rotateFromNorth(behind, Direction.NORTH));
        assertEquals(new Vec3i(1, 0, 0), Stations.rotateFromNorth(behind, Direction.WEST));
        assertEquals(new Vec3i(0, 0, -1), Stations.rotateFromNorth(behind, Direction.SOUTH));
        assertEquals(new Vec3i(-1, 0, 0), Stations.rotateFromNorth(behind, Direction.EAST));
    }
}
