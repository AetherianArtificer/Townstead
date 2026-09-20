package com.aetherianartificer.townstead.clothing;

import com.aetherianartificer.townstead.profession.def.ClothingChoice;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClothingEntryTest {

    @Test void generalResistanceQualifiesForBothProtectiveWardrobes() {
        var entry = ClothingEntry.parse(id("test:gear"), 0, json("{\"item\":\"test:coat\"}"));
        var resolved = entry.withThermal(new com.aetherianartificer.townstead.temperature.ThermalProtection(0, 0, 0, 8));
        assertTrue(resolved.isWarm());
        assertTrue(resolved.isCool());
        assertEquals(entry.id(), resolved.id());
    }

    @Test void resolvedProtectionDoesNotBreakAuthoredBodySetMembership() {
        var entry = ClothingEntry.parse(id("test:gear"), 0, json("{\"item\":\"test:coat\",\"material\":[\"wool\"]}"));
        var resolved = entry.withThermal(new com.aetherianartificer.townstead.temperature.ThermalProtection(.5f, 3, 0, 0));
        var selector = new com.aetherianartificer.townstead.clothing.policy.WardrobePolicy.Selector(null, false, true, null, null);
        assertTrue(com.aetherianartificer.townstead.clothing.dress.ClothingSelectors.admits(selector, resolved,
                com.aetherianartificer.townstead.culture.CultureClothing.NONE, java.util.List.of(entry)));
        assertEquals(entry.materials(), resolved.materials());
    }

    static ResourceLocation id(String s) {
        return com.aetherianartificer.townstead.data.DataPackLang.parseId(s);
    }

    static JsonObject json(String s) {
        return JsonParser.parseString(s).getAsJsonObject();
    }

    @Test
    void itemEntryParsesEveryField() {
        ClothingEntry entry = ClothingEntry.parse(id("test:doc"), 0, json("""
                { "id": "hood", "item": "accents:tundra_hood", "layer": "accessory", "slot": "head",
                  "thermal": { "offset": 0.5 }, "material": ["fur", "wool"], "spirit": "natural",
                  "occasion": ["festival"], "hair": "covered" }
                """));
        assertNotNull(entry);
        assertEquals(id("test:doc/hood"), entry.id());
        assertEquals(id("accents:tundra_hood"), entry.item());
        assertEquals(ClothingLayer.ACCESSORY, entry.layer());
        assertEquals(ClothingChannel.HEAD, entry.slot());
        assertEquals(0.5f, entry.thermal().offset());
        assertTrue(entry.isWarm());
        assertFalse(entry.isCool());
        assertEquals(java.util.Set.of("fur", "wool"), entry.materials());
        assertEquals(java.util.Set.of("natural"), entry.spirits());
        assertEquals(java.util.Set.of("festival"), entry.occasions());
        assertEquals(ClothingChoice.HairPolicy.COVERED, entry.hair());
        assertEquals("accents", entry.source());
    }

    @Test
    void skinEntryDefaultsToBaseLayerWholeBody() {
        ClothingEntry entry = ClothingEntry.parse(id("test:doc"), 3, json(
                "{ \"skin\": \"townstead_classic:highhold/*\", \"thermal\": { \"offset\": -0.5 } }"));
        assertNotNull(entry);
        assertEquals(id("test:doc/3"), entry.id());
        assertTrue(entry.isSkin());
        assertEquals(ClothingLayer.BASE, entry.layer());
        assertEquals(ClothingChannel.ALL, entry.slot());
        assertEquals(ClothingEntry.MCA_SKIN_SOURCE, entry.source());
        assertTrue(entry.isCool());
        assertTrue(entry.matchesSkin("townstead_classic:highhold/winter_1"));
        assertFalse(entry.matchesSkin("townstead_classic:ashmarch/1"));
    }

    @Test
    void exactSkinPatternMatchesOnlyItself() {
        ClothingEntry entry = ClothingEntry.parse(id("test:doc"), 0, json("{ \"skin\": \"mca:clothing/1\" }"));
        assertNotNull(entry);
        assertTrue(entry.matchesSkin("mca:clothing/1"));
        assertFalse(entry.matchesSkin("mca:clothing/10"));
    }

    @Test
    void tagEntryAcceptsHashPrefixAndArmorSpelling() {
        ClothingEntry entry = ClothingEntry.parse(id("test:doc"), 0, json(
                "{ \"tag\": \"#minecraft:wool\", \"layer\": \"armor\" }"));
        assertNotNull(entry);
        assertEquals(id("minecraft:wool"), entry.tag());
        assertEquals(ClothingLayer.ARMOUR, entry.layer());
        assertEquals("minecraft", entry.source());
    }

    @Test
    void entryMustNameExactlyOneThing() {
        assertNull(ClothingEntry.parse(id("test:doc"), 0, json("{ \"layer\": \"outerwear\" }")));
        assertNull(ClothingEntry.parse(id("test:doc"), 0, json(
                "{ \"item\": \"a:b\", \"skin\": \"c:d\" }")));
        assertNull(ClothingEntry.parse(id("test:doc"), 0, json(
                "{ \"skin\": \"c:d\", \"stack\": { \"type\": \"pheno:empty\" } }")));
    }

    @Test
    void zeroThermalReadsAsNoOpinion() {
        ClothingEntry entry = ClothingEntry.parse(id("test:doc"), 0, json(
                "{ \"item\": \"a:b\", \"thermal\": { \"offset\": 0 } }"));
        assertNotNull(entry);
        assertNull(entry.thermal());
        assertFalse(entry.isWarm());
    }

    @Test
    void queryMatchesOnEveryAxis() {
        ClothingEntry hat = ClothingEntry.parse(id("test:doc"), 0, json("""
                { "item": "accents:straw_hat", "layer": "accessory", "slot": "head",
                  "thermal": { "heat_resistance": 0.5 }, "material": "straw", "spirit": ["pastoral"] }
                """));
        assertTrue(ClothingQuery.ANY.test(hat));
        assertTrue(ClothingQuery.parse(json("{ \"thermal\": \"cool\", \"slot\": \"head\" }")).test(hat));
        assertFalse(ClothingQuery.parse(json("{ \"thermal\": \"warm\" }")).test(hat));
        assertTrue(ClothingQuery.parse(json("{ \"material\": [\"wool\", \"straw\"] }")).test(hat));
        assertFalse(ClothingQuery.parse(json("{ \"spirit\": \"nautical\" }")).test(hat));
        assertTrue(ClothingQuery.parse(json("{ \"source\": \"accents\" }")).test(hat));
        assertFalse(ClothingQuery.parse(json("{ \"layer\": \"base\" }")).test(hat));
    }

    @Test
    void wholeBodySkinSatisfiesAnySlotQuery() {
        ClothingEntry skin = ClothingEntry.parse(id("test:doc"), 0, json("{ \"skin\": \"a:b\" }"));
        assertTrue(ClothingQuery.parse(json("{ \"slot\": \"legs\" }")).test(skin));
    }
}
