package com.aetherianartificer.townstead.profession.def;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HerbalBrewsWitchTrackDataTest {

    private static final Set<String> AUDITED_HERBAL_IDS = Set.of(
            "herbalbrews:cauldron", "herbalbrews:jug", "herbalbrews:flask",
            "herbalbrews:witch_hat", "herbalbrews:herbal_infusion");

    @Test
    void witchIdentityIsSeparateAndHerbalProviderOwnsOnlyAuditedWorksites() {
        JsonObject profession = resource("/data/townstead/profession/witch/profession.json");
        JsonObject work = resource("/data/townstead/profession/witch/work.json");
        JsonObject provider = resource("/data/townstead/career_provider/witch_herbal_brews.json");

        assertEquals("career.townstead.witch",
                profession.getAsJsonObject("display_name").get("translate").getAsString());
        assertTrue(work.get("register_profession").getAsBoolean());
        assertTrue(work.getAsJsonArray("tasks").isEmpty(),
                "dynamic Cauldron work must not be advertised before its protocol exists");
        assertEquals("townstead:witch", provider.get("profession").getAsString());
        assertEquals("", provider.get("path").getAsString());
        assertEquals("herbalbrews", provider.get("mods").getAsString());

        JsonObject contributed = provider.getAsJsonObject("contributes").getAsJsonObject("profession");
        assertEquals(List.of("herbalbrews:witch_hat"), strings(contributed.getAsJsonArray("clothing")));
        JsonArray poi = contributed.getAsJsonArray("poi");
        assertEquals(List.of("herbalbrews:cauldron"),
                strings(poi.get(0).getAsJsonObject().getAsJsonArray("blocks")));
        assertEquals("compat/herbalbrews/witch_hut_l",
                poi.get(1).getAsJsonObject().getAsJsonArray("type_prefixes").get(0).getAsString());
        assertEquals(List.of(1, 3), poi.get(1).getAsJsonObject().getAsJsonArray("slots_per_tier")
                .asList().stream().map(element -> element.getAsInt()).toList());

        JsonObject barista = resource("/data/townstead/career_provider/barista_herbal_brews.json");
        String baristaJson = barista.toString();
        assertFalse(baristaJson.contains("herbalbrews:cauldron"));
        assertFalse(baristaJson.contains("herbalbrews:witch_hat"));
    }

    @Test
    void newlyAuthoredHutTiersArePairedAndUseOnlyAuditedHerbalBlocks() {
        for (String tier : List.of("witch_hut_l1", "witch_hut_l2")) {
            JsonObject mca = resource("/townstead_compat/building_types/compat/herbalbrews/"
                    + tier + ".json");
            JsonObject extended = resource("/data/townstead/extended_buildings/compat/herbalbrews/"
                    + tier + ".json");

            assertTrue(mca.get("noBeds").getAsBoolean());
            assertEquals("townstead:extended_building/v1", extended.get("schema").getAsString());
            assertEquals("herbalbrews", extended.get("mods").getAsString());
            assertEquals(List.of("townstead:witch"), strings(extended.getAsJsonArray("workers")));
            JsonObject spirit = extended.getAsJsonObject("spirit");
            assertTrue(spirit.get("haunted").getAsInt() > 0);
            assertEquals(spirit.get("magical").getAsInt(), spirit.get("haunted").getAsInt());
            assertFalse(spirit.has("scholar"));
            for (String block : mca.getAsJsonObject("blocks").keySet()) {
                if (block.startsWith("herbalbrews:")) {
                    assertTrue(AUDITED_HERBAL_IDS.contains(block), tier + " guessed block id " + block);
                }
            }
        }
    }

    @Test
    void semanticTagsUseAuditedIdsAndProtocolDomainsRemainUnavailable() {
        assertTagValue("block/compat/herbalbrews/witch_workstations", "herbalbrews:cauldron");
        assertTagValue("block/compat/herbalbrews/witch_service_vessels", "herbalbrews:jug");
        assertTagValue("item/compat/herbalbrews/witch_catalysts", "herbalbrews:herbal_infusion");
        assertTagValue("item/compat/herbalbrews/witch_flasks", "herbalbrews:flask");
        assertTagValue("item/compat/herbalbrews/witch_attire", "herbalbrews:witch_hat");

        JsonObject gaps = resource("/witch_track/herbal_brews_protocol_gaps.json");
        Set<String> expected = Set.of(
                "witchcraft.cauldron_component_transform",
                "witchcraft.cauldron_reaction",
                "witchcraft.flask_custody",
                "witchcraft.jug_service");
        Set<String> actual = gaps.getAsJsonArray("domains").asList().stream()
                .map(element -> element.getAsJsonObject())
                .peek(domain -> assertFalse(domain.get("available").getAsBoolean()))
                .map(domain -> domain.get("id").getAsString())
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(expected, actual);
    }

    private static void assertTagValue(String path, String expected) {
        String modern = "/data/townstead/tags/" + path + ".json";
        String legacy = modern.replace("/tags/block/", "/tags/blocks/")
                .replace("/tags/item/", "/tags/items/");
        var stream = HerbalBrewsWitchTrackDataTest.class.getResourceAsStream(modern);
        if (stream == null) stream = HerbalBrewsWitchTrackDataTest.class.getResourceAsStream(legacy);
        assertNotNull(stream, "missing Witch track tag: " + modern + " or " + legacy);
        JsonObject tag = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                .getAsJsonObject();
        JsonObject value = tag.getAsJsonArray("values").get(0).getAsJsonObject();
        assertEquals(expected, value.get("id").getAsString());
        assertFalse(value.get("required").getAsBoolean());
        assertTrue(AUDITED_HERBAL_IDS.contains(expected));
    }

    private static List<String> strings(JsonArray array) {
        return array.asList().stream().map(element -> element.getAsString()).toList();
    }

    private static JsonObject resource(String path) {
        var stream = HerbalBrewsWitchTrackDataTest.class.getResourceAsStream(path);
        assertNotNull(stream, "missing Witch track resource: " + path);
        return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                .getAsJsonObject();
    }
}
