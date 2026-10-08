package com.aetherianartificer.townstead.profession.def;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfessionWorkOverlayTest {

    @Test
    void sidecarOwnsRegistrationSitesAndTaskComposition() {
        JsonObject profession = object("""
                {"schema":"townstead:profession/v2","display_name":"Apiarist","hidden":false,
                 "paths":[{"id":"hive_keeper","skills":["steady_smoke"]}]}
                """);
        JsonObject work = object("""
                {"schema":"townstead:profession_work/v1",
                 "display_name":"Must not cross the boundary",
                 "register_profession":true,
                 "poi":[{"type":"townstead:job_block","block":"minecraft:beehive"}],
                 "storage":{"preferred_roles":["townstead:materials"]},
                 "tasks":[{"type":"townstead_work:interact"}],
                 "path_worksites":{"hive_keeper":["minecraft:beehive"]}}
                """);

        ProfessionWorkOverlay.apply(profession, work);

        assertTrue(profession.get("register_profession").getAsBoolean());
        assertEquals("minecraft:beehive",
                profession.getAsJsonArray("poi").get(0).getAsJsonObject().get("block").getAsString());
        assertEquals("townstead_work:interact",
                profession.getAsJsonArray("work_tasks").get(0).getAsJsonObject().get("type").getAsString());
        assertFalse(profession.has("trades"));
        assertEquals("townstead:materials", profession.getAsJsonObject("storage")
                .getAsJsonArray("preferred_roles").get(0).getAsString());
        assertEquals("Apiarist", profession.get("display_name").getAsString());
        assertEquals("minecraft:beehive", profession.getAsJsonArray("paths").get(0)
                .getAsJsonObject().getAsJsonArray("worksites").get(0).getAsString());
        assertFalse(profession.has("tasks"), "the parser continues to consume its established internal key");
    }

    @Test
    void wrongSidecarSchemaIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> ProfessionWorkOverlay.apply(
                object("{}"), object("{\"schema\":\"townstead:profession/v2\"}")));
    }

    @Test
    void sidecarSchemaIsRequired() {
        assertThrows(IllegalArgumentException.class, () -> ProfessionWorkOverlay.apply(
                object("{}"), object("{}")));
    }

    @Test
    void sidecarRejectsMerchantOffers() {
        JsonObject profession = object("{}");
        assertThrows(IllegalArgumentException.class, () -> ProfessionWorkOverlay.apply(
                profession, object("""
                        {"schema":"townstead:profession_work/v1",
                         "register_profession":true,"trades":{}}
                        """)));
        assertFalse(profession.has("register_profession"), "an invalid sidecar applies nothing");
    }

    @Test
    void sidecarCannotInventAPath() {
        assertThrows(IllegalArgumentException.class, () -> ProfessionWorkOverlay.apply(
                object("{\"paths\":[{\"id\":\"hive_keeper\"}]}"),
                object("{\"schema\":\"townstead:profession_work/v1\","
                        + "\"path_worksites\":{\"field_keeper\":[\"minecraft:beehive\"]}}")));
    }

    @Test
    void baseWorkKeepsPathAuthoredContributions() {
        JsonObject profession = object("""
                {"work_tasks":[
                  {"type":"townstead_work:chop",
                   "__townstead_path_contribution":"pizzaiolo"}
                ]}
                """);
        ProfessionWorkOverlay.apply(profession, object("""
                {"schema":"townstead:profession_work/v1",
                 "tasks":[{"type":"townstead_work:cook"}]}
                """));

        var tasks = profession.getAsJsonArray("work_tasks");
        assertEquals(2, tasks.size());
        assertEquals("townstead_work:cook",
                tasks.get(0).getAsJsonObject().get("type").getAsString());
        assertEquals("townstead_work:chop",
                tasks.get(1).getAsJsonObject().get("type").getAsString());
        assertEquals("pizzaiolo", tasks.get(1).getAsJsonObject()
                .get(ProfessionPathDocument.CONTRIBUTION_ORIGIN).getAsString());
    }

    private static JsonObject object(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }
}
