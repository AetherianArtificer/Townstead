package com.aetherianartificer.townstead.story.goal;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoalsTest {

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    @Test
    void parametersKeepTheirTypeWhenAlone() {
        JsonObject out = Goals.substitute(json("""
                { "target": "$count", "where": { "profession": "minecraft:$profession" }, "list": ["$profession"] }
                """), Map.of("count", "3", "profession", "farmer")).getAsJsonObject();
        assertEquals(3, out.get("target").getAsInt());
        assertTrue(out.get("target").getAsJsonPrimitive().isNumber());
        assertEquals("minecraft:farmer", out.getAsJsonObject("where").get("profession").getAsString());
        assertEquals("farmer", out.getAsJsonArray("list").get(0).getAsString());
    }

    @Test
    void longerParameterNamesWinOverTheirPrefixes() {
        JsonObject out = Goals.substitute(json("{ \"a\": \"x-$count-$count_max\" }"),
                Map.of("count", "1", "count_max", "9")).getAsJsonObject();
        assertEquals("x-1-9", out.get("a").getAsString());
    }

    @Test
    void wrongNumberOfValuesIsReported() {
        Goals.Parsed parsed = Goals.build(json("{ \"params\": [\"profession\", \"count\"], \"text\": \"t\" }"), List.of("farmer"));
        assertNull(parsed.goal());
        assertTrue(parsed.error().contains("expects 2 values (profession, count) but got 1"), parsed.error());
    }

    @Test
    void unknownGoalNamesAreReported() {
        Goals.Parsed parsed = Goals.resolve("feild_post", "townstead", Map.of());
        assertNull(parsed.goal());
        assertNotNull(parsed.error());
        assertTrue(parsed.error().contains("feild_post"), parsed.error());
    }

    @Test
    void goalsNeedTextAndAShape() {
        assertTrue(Goals.build(json("{ \"count\": 2 }"), List.of()).error().contains("\"text\""));
        assertTrue(Goals.build(json("{ \"text\": \"t\" }"), List.of()).error().contains("one of"));
        assertTrue(Goals.build(json("{ \"text\": \"t\", \"event\": \"townstead:nope\" }"), List.of()).error().contains("no event"));
    }

    @Test
    void eventIdsAreSnakeCase() {
        assertEquals("townstead:work_completed",
                GoalEvents.id(com.aetherianartificer.townstead.api.v1.event.WorkCompletedEvent.class));
        assertNotNull(GoalEvents.byId("townstead:village_needs_band_changed"));
        assertNotNull(GoalEvents.byId("work_completed"));
    }

    @Test
    void distinctCountsNeedARealEventField() {
        Goals.Parsed ok = Goals.build(json("""
                { "text": "Harvest {count} kinds", "count": 5, "event": "townstead:work_completed",
                  "where": { "verb": "townstead:harvested" }, "distinct": "objectId" }
                """), List.of());
        assertNotNull(ok.goal(), ok.error());
        assertTrue(ok.goal().isDistinct());
        Goals.Parsed bad = Goals.build(json("""
                { "text": "t", "event": "townstead:work_completed", "distinct": "crop" }
                """), List.of());
        assertNull(bad.goal());
        assertTrue(bad.error().contains("no field 'crop'"), bad.error());
    }

    @Test
    void seasonsGoalsNeedACondition() {
        Goals.Parsed parsed = Goals.build(json("{ \"text\": \"t\", \"seasons\": 4 }"), List.of());
        assertNull(parsed.goal());
        assertTrue(parsed.error().contains("needs \"condition\""), parsed.error());
    }
}
