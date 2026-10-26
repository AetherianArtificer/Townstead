package com.aetherianartificer.townstead.story.reward;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RewardsTest {

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    @Test
    void aRewardNeedsSomethingToGive() {
        Rewards.Parsed parsed = Rewards.build(json("{ \"text\": \"Nothing\" }"), List.of());
        assertNull(parsed.reward());
        assertTrue(parsed.error().contains("\"items\", \"loot_table\" or \"action\""), parsed.error());
    }

    @Test
    void lootTablesNeedTextForTheLedger() {
        Rewards.Parsed parsed = Rewards.build(json("{ \"loot_table\": \"minecraft:chests/village/village_plains_house\" }"), List.of());
        assertNull(parsed.reward());
        assertTrue(parsed.error().contains("needs \"text\""), parsed.error());
    }

    @Test
    void parameterCountIsChecked() {
        Rewards.Parsed parsed = Rewards.build(json("{ \"params\": [\"count\"], \"text\": \"t\" }"), List.of());
        assertNull(parsed.reward());
        assertTrue(parsed.error().contains("expects 1 values (count) but got 0"), parsed.error());
    }

    @Test
    void unknownRewardNamesAreReported() {
        Rewards.Parsed parsed = Rewards.resolve("seed_bgs", "townstead", Map.of());
        assertNull(parsed.reward());
        assertTrue(parsed.error().contains("seed_bgs"), parsed.error());
    }
}
