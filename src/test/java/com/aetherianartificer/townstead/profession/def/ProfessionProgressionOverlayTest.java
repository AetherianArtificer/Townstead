package com.aetherianartificer.townstead.profession.def;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProfessionProgressionOverlayTest {

    @Test
    void ranksLowerToTheExistingProgressionModel() {
        JsonObject profession = object("{\"display_name\":\"Beekeeper\"}");
        ProfessionProgressionOverlay.apply(profession, object("""
                {"schema":"townstead:profession_progression/v1",
                 "daily_cap":80,"ranks":[0,40,120]}
                """));

        assertEquals(3, profession.getAsJsonArray("levels").size());
        assertEquals(40, profession.getAsJsonArray("levels").get(0)
                .getAsJsonObject().get("xp").getAsInt());
        assertEquals(80, profession.getAsJsonArray("levels").get(1)
                .getAsJsonObject().get("xp").getAsInt());
        assertFalse(profession.getAsJsonArray("levels").get(2).getAsJsonObject().has("xp"));
        assertEquals(80, profession.get("daily_cap").getAsInt());
        assertEquals(120, profession.get("max_xp").getAsInt());
        assertFalse(profession.has("ranks"));
        assertEquals("Beekeeper", profession.get("display_name").getAsString());
    }

    @Test
    void ranksAreRequired() {
        assertThrows(IllegalArgumentException.class, () -> ProfessionProgressionOverlay.apply(
                object("{}"), object("{\"schema\":\"townstead:profession_progression/v1\"}")));
    }

    @Test
    void schemaIsRequired() {
        assertThrows(IllegalArgumentException.class, () -> ProfessionProgressionOverlay.apply(
                object("{}"), object("{\"ranks\":[0]}")));
    }

    @Test
    void expandedRanksKeepOnlyExceptionalFields() {
        JsonObject profession = object("{}");
        ProfessionProgressionOverlay.apply(profession, object("""
                {"schema":"townstead:profession_progression/v1",
                 "ranks":[0,{"at":40,"name":{"text":"Apiarist"},"skill_points":2},120]}
                """));
        JsonObject second = profession.getAsJsonArray("levels").get(1).getAsJsonObject();
        assertEquals(80, second.get("xp").getAsInt());
        assertEquals(2, second.get("skill_points").getAsInt());
        assertEquals("Apiarist", second.getAsJsonObject("name").get("text").getAsString());
        assertFalse(second.has("at"));
        assertEquals(120, profession.get("max_xp").getAsInt());
    }

    @Test
    void oldSpanRanksAndMaxXpAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> ProfessionProgressionOverlay.apply(
                object("{}"), object("""
                        {"schema":"townstead:profession_progression/v1","ranks":[{"xp":40}]}
                        """)));
        assertThrows(IllegalArgumentException.class, () -> ProfessionProgressionOverlay.apply(
                object("{}"), object("""
                        {"schema":"townstead:profession_progression/v1","max_xp":1000,
                         "ranks":[0,40]}
                        """)));
    }

    private static JsonObject object(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }
}
