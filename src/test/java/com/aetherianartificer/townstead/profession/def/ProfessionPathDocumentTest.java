package com.aetherianartificer.townstead.profession.def;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProfessionPathDocumentTest {

    @Test
    void filenameAndSkillPositionsDeriveTheCompletePathShape() {
        JsonObject profession = object("{\"schema\":\"townstead:profession/v2\"}");
        ProfessionPathDocument.Applied applied = ProfessionPathDocument.apply(
                profession, "hive_keeper", object("""
                        {"schema":"townstead:profession_path/v1",
                         "name":"Hive Keeper","title":"Apiarist","color":"#D6A928",
                         "skills":[
                           "smoker_use",
                           ["protective_clothing","veil_mending"],
                           "first_aid"
                         ]}
                        """));

        JsonObject path = profession.getAsJsonArray("paths").get(0).getAsJsonObject();
        assertEquals("hive_keeper", path.get("id").getAsString());
        assertEquals("hive_keeper/smoker_use", path.get("gateway").getAsString());
        assertEquals(3, path.getAsJsonArray("skills").size());
        assertEquals(1, applied.skillTiers().get("hive_keeper/smoker_use"));
        assertEquals(2, applied.skillTiers().get("hive_keeper/protective_clothing"));
        assertEquals(2, applied.skillTiers().get("hive_keeper/veil_mending"));
        assertEquals(3, applied.skillTiers().get("hive_keeper/first_aid"));
        assertEquals(4, profession.getAsJsonArray("skills").size());
        assertEquals(4, profession.getAsJsonArray("titles").get(0).getAsJsonObject()
                .getAsJsonArray("skills").size());
    }

    @Test
    void aSkillCannotBelongToTwoPaths() {
        JsonObject profession = object("{}");
        ProfessionPathDocument.apply(profession, "hive_keeper",
                object("{\"skills\":[\"example:shared_skill\"]}"));

        assertThrows(IllegalArgumentException.class, () -> ProfessionPathDocument.apply(
                profession, "field_keeper",
                object("{\"skills\":[\"example:shared_skill\"]}")));
    }

    @Test
    void pathIdComesFromItsDirectory() {
        assertThrows(IllegalArgumentException.class, () -> ProfessionPathDocument.apply(
                object("{}"), "hive_keeper",
                object("{\"id\":\"other\",\"skills\":[\"smoker_use\"]}")));
    }

    @Test
    void unpublishedLevelWrapperSyntaxIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> ProfessionPathDocument.apply(
                object("{}"), "hive_keeper",
                object("{\"levels\":[{\"skills\":[\"smoker_use\"]}]}")));
    }

    private static JsonObject object(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }
}
