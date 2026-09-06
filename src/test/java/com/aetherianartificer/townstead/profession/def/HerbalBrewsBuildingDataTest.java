package com.aetherianartificer.townstead.profession.def;

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

class HerbalBrewsBuildingDataTest {

    private static final List<String> BUILDINGS = List.of(
            "tea_house_l1", "tea_house_l2", "tea_house_l3",
            "herbal_dryhouse_l1",
            "herb_garden_l1", "herb_garden_l2");
    private static final Set<String> AUDITED_BLOCKS = Set.of(
            "herbalbrews:tea_kettle", "herbalbrews:copper_tea_kettle", "herbalbrews:stove",
            "herbalbrews:tea_leaf_crate", "herbalbrews:green_tea_leaf_block",
            "herbalbrews:dried_green_tea_leaf_block", "herbalbrews:mixed_tea_leaf_block",
            "herbalbrews:oolong_tea_leaf_block", "herbalbrews:herbalbrews_banner",
            "herbalbrews:tea_plant", "herbalbrews:coffee_plant", "herbalbrews:rooibos_plant",
            "herbalbrews:yerba_mate_plant", "herbalbrews:hibiscus", "herbalbrews:lavender");

    @Test
    void everyMcaBuildingHasTownsteadMetadataAndOnlyAuditedHerbalBlocks() {
        for (String building : BUILDINGS) {
            JsonObject mca = resource("/townstead_compat/building_types/compat/herbalbrews/"
                    + building + ".json");
            JsonObject extended = resource("/data/townstead/extended_buildings/compat/herbalbrews/"
                    + building + ".json");
            assertEquals("townstead:extended_building/v1", extended.get("schema").getAsString());
            assertTrue(mca.get("noBeds").getAsBoolean());
            for (String block : mca.getAsJsonObject("blocks").keySet()) {
                if (block.startsWith("herbalbrews:")) {
                    assertTrue(AUDITED_BLOCKS.contains(block), building + " guessed block id " + block);
                    assertFalse(block.equals("herbalbrews:cauldron"),
                            "the witch Cauldron is not Barista/building work in this wave");
                }
            }
        }
    }

    private static JsonObject resource(String path) {
        var stream = HerbalBrewsBuildingDataTest.class.getResourceAsStream(path);
        assertNotNull(stream, "missing paired building resource: " + path);
        return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                .getAsJsonObject();
    }
}
