package com.aetherianartificer.townstead.profession.def;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProfessionPathsOverlayTest {

    @Test
    void sidecarReplacesOnlyPaths() {
        JsonObject profession = object("""
                {"schema":"townstead:profession/v2","display_name":"Apiarist",
                 "paths":[{"id":"old"}],
                 "titles":[{"id":"veteran","name":"Veteran","skills":["first_aid"]},
                  {"id":"hive_keeper","name":"Old duplicate","skills":["smoker_use"]}]}
                """);
        JsonObject paths = object("""
                {"schema":"townstead:profession_paths/v1",
                 "paths":[{"id":"hive_keeper","gateway":"smoker_use",
                  "skills":["protective_clothing","first_aid"],"title":"Hive Keeper"}]}
                """);

        ProfessionPathsOverlay.apply(profession, paths);

        assertEquals("Apiarist", profession.get("display_name").getAsString());
        assertEquals("hive_keeper", profession.getAsJsonArray("paths").get(0)
                .getAsJsonObject().get("id").getAsString());
        assertEquals(2, profession.getAsJsonArray("titles").size(),
                "standalone titles remain and a path title replaces the same standalone id");
        JsonObject title = profession.getAsJsonArray("titles").get(1).getAsJsonObject();
        assertEquals("hive_keeper", title.get("id").getAsString());
        assertEquals("Hive Keeper", title.get("name").getAsString());
        assertEquals(3, title.getAsJsonArray("skills").size());
        assertEquals("smoker_use", title.getAsJsonArray("skills").get(0).getAsString());
    }

    @Test
    void sidecarRequiresAPathArray() {
        assertThrows(IllegalArgumentException.class, () -> ProfessionPathsOverlay.apply(
                object("{}"), object("{\"schema\":\"townstead:profession_paths/v1\"}")));
    }

    @Test
    void completionTitleRequiresARealPathGateway() {
        assertThrows(IllegalArgumentException.class, () -> ProfessionPathsOverlay.apply(
                object("{}"), object("""
                        {"paths":[{"id":"hive_keeper","title":"Hive Keeper"}]}
                        """)));
    }

    private static JsonObject object(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }
}
