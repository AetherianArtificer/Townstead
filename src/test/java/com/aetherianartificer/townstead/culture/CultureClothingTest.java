package com.aetherianartificer.townstead.culture;

import com.aetherianartificer.townstead.clothing.ClothingEntry;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CultureClothingTest {

    static ResourceLocation id(String s) {
        return com.aetherianartificer.townstead.data.DataPackLang.parseId(s);
    }

    static JsonObject json(String s) {
        return JsonParser.parseString(s).getAsJsonObject();
    }

    static final JsonObject CULTURE = json("""
            { "schema": "townstead:culture/v1", "clothing": {
                "skins": [ { "pattern": "tc:highhold/", "rate": 6 },
                           { "pattern": "mca:winter*", "rate": 2, "spirit": ["pastoral"] } ],
                "types": [ { "select": { "material": "wool" }, "rate": 4 },
                           { "select": { "slot": "head" }, "rate": 2, "spirit": ["scholar"] },
                           { "select": {}, "rate": 0 } ],
                "sets": [ "tc:highhold_dress", "not an id::" ],
                "palette": [ "#5A3E2B", "C9B37E", "nope" ]
            } }
            """);

    @Test
    void parsesEveryBlockAndDropsBadEntries() {
        CultureClothing clothing = CultureClothing.parse(CULTURE);
        assertEquals(2, clothing.skins().size());
        assertEquals(2, clothing.types().size());
        assertEquals(List.of(id("tc:highhold_dress")), clothing.sets());
        assertEquals(List.of(0x5A3E2B, 0xC9B37E), clothing.palette());
        assertSame(CultureClothing.NONE, CultureClothing.parse(json("{}")));
        assertSame(CultureClothing.NONE, CultureClothing.parse(json("{ \"clothing\": { \"skins\": [] } }")));
    }

    @Test
    void skinRatesMultiplyAndSpiritScalesThem() {
        CultureClothing clothing = CultureClothing.parse(CULTURE);
        assertEquals(6.0, clothing.rateForSkin("tc:highhold/1", CultureClothing.shares(Map.of())));
        assertEquals(1.0, clothing.rateForSkin("tc:ashmarch/1", CultureClothing.shares(Map.of())));
        assertEquals(2.0, clothing.rateForSkin("mca:winter_coat", CultureClothing.shares(Map.of())));
        assertEquals(3.0, clothing.rateForSkin("mca:winter_coat", CultureClothing.shares(Map.of("pastoral", 0.5))));
    }

    @Test
    void typeRatesReadTheEntryThroughTheQuery() {
        CultureClothing clothing = CultureClothing.parse(CULTURE);
        ClothingEntry woolHat = ClothingEntry.parse(id("t:d"), 0, json(
                "{ \"item\": \"m:hat\", \"slot\": \"head\", \"material\": \"wool\" }"));
        ClothingEntry linenShirt = ClothingEntry.parse(id("t:d"), 1, json(
                "{ \"item\": \"m:shirt\", \"slot\": \"body\", \"material\": \"linen\" }"));
        assertNotNull(woolHat);
        assertNotNull(linenShirt);
        assertEquals(8.0, clothing.rateFor(woolHat, CultureClothing.shares(Map.of())));
        assertEquals(16.0, clothing.rateFor(woolHat, CultureClothing.shares(Map.of("scholar", 1.0))));
        assertEquals(1.0, clothing.rateFor(linenShirt, CultureClothing.shares(Map.of("scholar", 1.0))));
        assertTrue(CultureClothing.NONE.rateFor(woolHat, CultureClothing.shares(Map.of())) == 1.0);
    }

    @Test
    void cultureRecordDefaultsToNoClothing() {
        Culture culture = new Culture(id("t:c"), net.minecraft.network.chat.Component.literal("c"), null);
        assertSame(CultureClothing.NONE, culture.clothing());
    }
}
