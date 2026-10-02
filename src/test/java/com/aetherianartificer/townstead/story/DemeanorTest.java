package com.aetherianartificer.townstead.story;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DemeanorTest {

    private static String of(Map<String, Double> values) {
        return Demeanor.of(Demeanor.DEFAULT, q -> values.getOrDefault(q, 0.0));
    }

    @Test
    void defaultBandsFollowTheDesign() {
        assertEquals("neutral", of(Map.of()));
        assertEquals("warm", of(Map.of("trust", 25.0)));
        assertEquals("jovial", of(Map.of("trust", 25.0, "affection", 35.0)));
        assertEquals("guarded", of(Map.of("trust", -5.0)));
        assertEquals("guarded", of(Map.of("affection", 35.0, "resentment", 25.0)));
        assertEquals("stern", of(Map.of("affection", 80.0, "resentment", 45.0)));
        assertEquals("stern", of(Map.of("fear", 30.0)));
    }

    @Test
    void customBandsReplaceTheDefault() {
        List<String> issues = new ArrayList<>();
        List<Demeanor.Band> bands = Demeanor.parse(JsonParser.parseString("""
                [ { "name": "Smitten", "all": { "attraction": { "min": 50 } } }, { "name": "polite" } ]
                """), issues);
        assertTrue(issues.isEmpty(), issues.toString());
        assertEquals("smitten", Demeanor.of(bands, q -> q.equals("attraction") ? 60 : 0));
        assertEquals("polite", Demeanor.of(bands, q -> 0));
    }

    @Test
    void bandsNeedNames() {
        List<String> issues = new ArrayList<>();
        assertNull(Demeanor.parse(JsonParser.parseString("[ { \"all\": {} } ]"), issues));
        assertEquals(List.of("error: each demeanor band needs a \"name\""), issues);
    }
}
