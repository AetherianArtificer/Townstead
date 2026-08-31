package com.aetherianartificer.townstead.work.station;

import com.aetherianartificer.townstead.work.recipe.StationType;
import net.minecraft.resources.ResourceLocation;
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
}
