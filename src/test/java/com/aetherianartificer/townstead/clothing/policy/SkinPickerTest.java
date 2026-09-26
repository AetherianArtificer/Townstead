package com.aetherianartificer.townstead.clothing.policy;

import com.aetherianartificer.townstead.clothing.ClothingDefs;
import com.aetherianartificer.townstead.clothing.ClothingEntry;
import com.aetherianartificer.townstead.clothing.ClothingQuery;
import com.aetherianartificer.townstead.clothing.ClothingSet;
import com.aetherianartificer.townstead.culture.CultureClothing;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkinPickerTest {

    static ResourceLocation id(String s) {
        return com.aetherianartificer.townstead.data.DataPackLang.parseId(s);
    }

    static JsonObject json(String s) {
        return JsonParser.parseString(s).getAsJsonObject();
    }

    @AfterEach
    void reset() {
        ClothingDefs.replaceAll(List.of(), List.of());
    }

    @Test
    void seededChoiceIsStableAndFollowsWeight() {
        List<SkinPicker.Candidate> candidates = List.of(
                new SkinPicker.Candidate("a", null, 1.0),
                new SkinPicker.Candidate("b", null, 99.0));
        int b = 0;
        for (int seed = 0; seed < 200; seed++) {
            String first = SkinPicker.choose(candidates, new Random(seed));
            assertEquals(first, SkinPicker.choose(candidates, new Random(seed)));
            if ("b".equals(first)) b++;
        }
        assertTrue(b > 180, "b should win almost every draw, won " + b);
        assertNull(SkinPicker.choose(List.of(new SkinPicker.Candidate("z", null, 0)), new Random(1)));
    }

    @Test
    void spiritAffinityMultipliesByShare() {
        ClothingEntry entry = ClothingEntry.parse(id("t:d"), 0, json(
                "{ \"skin\": \"a:b\", \"spirit\": [\"nautical\", \"scholar\"] }"));
        assertNotNull(entry);
        assertEquals(1.0, SkinPicker.spiritAffinity(entry, axis -> 0.0));
        assertEquals(1.5 * 1.25, SkinPicker.spiritAffinity(entry,
                axis -> axis.equals("nautical") ? 0.5 : axis.equals("scholar") ? 0.25 : 0.0), 1e-9);
    }

    @Test
    void selectorsAdmitByPatternSetCultureBodyAndQuery() {
        ClothingEntry winter = ClothingEntry.parse(id("t:d"), 0, json(
                "{ \"id\": \"winter\", \"skin\": \"tc:winter/*\", \"thermal\": { \"offset\": 0.5 } }"));
        ClothingEntry fitted = ClothingEntry.parse(id("t:d"), 1, json(
                "{ \"id\": \"fitted\", \"skin\": \"tc:webster/1\" }"));
        ClothingSet set = ClothingSet.parse(id("t:set"), json(
                "{ \"weight\": 3, \"members\": [ { \"ref\": \"t:d/winter\" } ] }"), Map.of());
        assertNotNull(winter);
        assertNotNull(fitted);
        assertNotNull(set);
        ClothingDefs.replaceAll(List.of(winter, fitted), List.of(set));

        WardrobePolicy.Selector byPattern = new WardrobePolicy.Selector(null, false, false, "tc:winter/", null);
        assertEquals(1.0, SkinPicker.selectorWeight(byPattern, "tc:winter/3", winter, CultureClothing.NONE, List.of()));
        assertEquals(0.0, SkinPicker.selectorWeight(byPattern, "tc:summer/3", null, CultureClothing.NONE, List.of()));

        WardrobePolicy.Selector bySet = new WardrobePolicy.Selector(id("t:set"), false, false, null, null);
        assertEquals(3.0, SkinPicker.selectorWeight(bySet, "tc:winter/3", winter, CultureClothing.NONE, List.of()));
        assertEquals(0.0, SkinPicker.selectorWeight(bySet, "tc:webster/1", fitted, CultureClothing.NONE, List.of()));

        CultureClothing culture = CultureClothing.parse(json(
                "{ \"clothing\": { \"sets\": [\"t:set\"], \"skins\": [ { \"pattern\": \"tc:festival/\", \"rate\": 2 } ] } }"));
        WardrobePolicy.Selector byCulture = new WardrobePolicy.Selector(null, true, false, null, null);
        assertEquals(3.0, SkinPicker.selectorWeight(byCulture, "tc:winter/3", winter, culture, List.of()));
        assertEquals(1.0, SkinPicker.selectorWeight(byCulture, "tc:festival/1", null, culture, List.of()));
        assertEquals(0.0, SkinPicker.selectorWeight(byCulture, "tc:plain/1", null, culture, List.of()));

        WardrobePolicy.Selector byBody = new WardrobePolicy.Selector(null, false, true, null, null);
        assertEquals(1.0, SkinPicker.selectorWeight(byBody, "tc:webster/1", fitted, CultureClothing.NONE, List.of(fitted)));
        assertEquals(0.0, SkinPicker.selectorWeight(byBody, "tc:winter/3", winter, CultureClothing.NONE, List.of(fitted)));

        WardrobePolicy.Selector byQuery = new WardrobePolicy.Selector(null, false, false, null,
                ClothingQuery.parse(json("{ \"thermal\": \"warm\" }")));
        assertEquals(1.0, SkinPicker.selectorWeight(byQuery, "tc:winter/3", winter, CultureClothing.NONE, List.of()));
        assertEquals(0.0, SkinPicker.selectorWeight(byQuery, "tc:webster/1", fitted, CultureClothing.NONE, List.of()));
        assertEquals(0.0, SkinPicker.selectorWeight(byQuery, "mca:unknown", null, CultureClothing.NONE, List.of()));
        assertEquals(1.0, SkinPicker.selectorWeight(WardrobePolicy.Selector.ANYTHING, "mca:unknown", null,
                CultureClothing.NONE, List.of()));
    }
}
