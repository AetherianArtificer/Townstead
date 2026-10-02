package com.aetherianartificer.townstead.clothing;

import com.aetherianartificer.townstead.temperature.ThermalProtection;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ClothingThermalTest {

    @Test
    void coldClimateSkinsWarmAndHotClimateSkinsCool() {
        assertEquals(new ThermalProtection(0.5f, 0, 0, 0), ClothingThermal.fromMcaClimate(-2));
        assertEquals(new ThermalProtection(0.25f, 0, 0, 0), ClothingThermal.fromMcaClimate(-1));
        assertSame(ThermalProtection.NONE, ClothingThermal.fromMcaClimate(0));
        assertEquals(new ThermalProtection(-0.5f, 0, 0, 0), ClothingThermal.fromMcaClimate(2));
    }

    @Test
    void wholeOutfitOfColdSkinsStaysInsideOneTaggedPiece() {
        assertEquals(0.5f, Math.abs(ClothingThermal.fromMcaClimate(-2).offset()));
    }
    @Test
    void providerWarmthMakesASyntheticStackEntry() {
        var itemId = com.aetherianartificer.townstead.data.DataPackLang.parseId("wp:sweater_wool");
        ThermalProtection warm = new ThermalProtection(0f, 0.5f, 0f, 0f);

        ClothingEntry body = ClothingThermal.syntheticStackEntry(itemId, warm, ClothingChannel.BODY, false);
        assertEquals(ClothingLayer.OUTERWEAR, body.layer());
        assertEquals(ClothingChannel.BODY, body.slot());
        assertEquals(itemId, body.item());
        assertEquals("townstead:provider/wp/sweater_wool", body.id().toString());
        assertEquals(true, body.isWarm());

        ClothingEntry hat = ClothingThermal.syntheticStackEntry(itemId, warm, ClothingChannel.HEAD, false);
        assertEquals(ClothingLayer.ACCESSORY, hat.layer());

        ClothingEntry unknown = ClothingThermal.syntheticStackEntry(itemId, warm, null, false);
        assertEquals(ClothingLayer.OUTERWEAR, unknown.layer());
        assertEquals(ClothingChannel.ALL, unknown.slot());

        ClothingEntry armour = ClothingThermal.syntheticStackEntry(itemId, warm, ClothingChannel.BODY, true);
        assertEquals(ClothingLayer.ARMOUR, armour.layer());

        assertEquals(null, ClothingThermal.syntheticStackEntry(itemId, ThermalProtection.NONE, ClothingChannel.BODY, false));
        assertEquals(null, ClothingThermal.syntheticStackEntry(itemId, null, ClothingChannel.BODY, false));
    }
}
