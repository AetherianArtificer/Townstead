package com.aetherianartificer.townstead.clothing;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClothingSetTest {

    static ResourceLocation id(String s) {
        return com.aetherianartificer.townstead.data.DataPackLang.parseId(s);
    }

    static JsonObject json(String s) {
        return JsonParser.parseString(s).getAsJsonObject();
    }

    static ClothingEntry entry(String doc, String id, String body) {
        ClothingEntry parsed = ClothingEntry.parse(id(doc), 0, json("{ \"id\": \"" + id + "\", " + body + " }"));
        assertNotNull(parsed);
        return parsed;
    }

    @AfterEach
    void reset() {
        ClothingDefs.replaceAll(List.of(), List.of());
    }

    @Test
    void refsSelectsAndInlineMembersResolveAfterLoad() {
        ClothingEntry sweater = entry("test:wp", "sweater",
                "\"item\": \"wp:sweater\", \"thermal\": { \"offset\": 0.5 }, \"material\": \"wool\"");
        ClothingEntry shirt = entry("test:wp", "shirt",
                "\"item\": \"wp:shirt\", \"thermal\": { \"offset\": -0.25 }, \"material\": \"cotton\"");
        ClothingEntry hat = entry("test:acc", "hat",
                "\"item\": \"acc:hat\", \"layer\": \"accessory\", \"slot\": \"head\"");
        ClothingSet set = ClothingSet.parse(id("test:winter"), json("""
                { "schema": "townstead:clothing_set/v1", "members": [
                    { "ref": "test:acc/hat" },
                    { "select": { "thermal": "warm" } },
                    { "item": "mca:scarf", "slot": "neck" },
                    { "ref": "test:missing/x" }
                ] }
                """), Map.of());
        assertNotNull(set);
        ClothingDefs.replaceAll(List.of(sweater, shirt, hat), List.of(set));

        List<ClothingEntry> members = ClothingDefs.members(id("test:winter"));
        assertEquals(3, members.size());
        assertEquals(hat, members.get(0));
        assertEquals(sweater, members.get(1));
        assertEquals(id("test:winter/2"), members.get(2).id());
        assertTrue(ClothingDefs.members(id("test:nope")).isEmpty());
    }

    @Test
    void includesExpandAndCyclesAreCut() {
        ClothingEntry a = entry("test:d", "a", "\"item\": \"m:a\"");
        ClothingEntry b = entry("test:d", "b", "\"item\": \"m:b\"");
        ClothingSet one = ClothingSet.parse(id("test:one"), json(
                "{ \"include\": [\"test:two\"], \"members\": [ { \"ref\": \"test:d/a\" } ] }"), Map.of());
        ClothingSet two = ClothingSet.parse(id("test:two"), json(
                "{ \"include\": \"test:one\", \"members\": [ { \"ref\": \"test:d/b\" } ], \"weight\": 2 }"), Map.of());
        assertNotNull(one);
        assertNotNull(two);
        ClothingDefs.replaceAll(List.of(a, b), List.of(one, two));

        assertEquals(List.of(b, a), ClothingDefs.members(id("test:one")));
        assertEquals(List.of(a, b), ClothingDefs.members(id("test:two")));
        assertEquals(2f, ClothingDefs.set(id("test:two")).weight());
    }

    @Test
    void emptySetIsRefused() {
        assertNull(ClothingSet.parse(id("test:empty"), json("{ \"members\": [] }"), Map.of()));
        assertNull(ClothingSet.parse(id("test:junk"), json("{ \"members\": [ { \"layer\": \"base\" } ] }"), Map.of()));
    }

    @Test
    void queryOverAllEntries() {
        ClothingEntry warm = entry("test:d", "w", "\"item\": \"m:w\", \"thermal\": { \"cold_resistance\": 1 }");
        ClothingEntry cool = entry("test:d", "c", "\"item\": \"m:c\", \"thermal\": { \"offset\": -1 }");
        ClothingDefs.replaceAll(List.of(warm, cool), List.of());
        assertEquals(List.of(warm), ClothingDefs.query(ClothingQuery.parse(json("{ \"thermal\": \"warm\" }"))));
        assertEquals(List.of(warm, cool), ClothingDefs.query(ClothingQuery.ANY));
    }
}
