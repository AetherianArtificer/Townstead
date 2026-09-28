package com.aetherianartificer.townstead.persona;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersonasTest {
    private static final ResourceLocation ID = ResourceLocation.tryParse("test:wanderer");

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    @Test
    void anEmptyPersonaLoadsWithDefaults() {
        List<String> issues = new ArrayList<>();
        PersonaDefinition persona = Personas.parse(ID, json("{}"), Map.of(), issues);
        assertNotNull(persona);
        assertEquals("wanderer", persona.name());
        assertEquals(PersonaDefinition.Arrival.WALK_IN, persona.arrival());
        assertTrue(persona.downed());
        assertEquals(ResourceLocation.tryParse("test:persona/wanderer"), persona.story());
        assertTrue(issues.stream().anyMatch(i -> i.contains("only comes through /townstead persona spawn")), issues.toString());
    }

    @Test
    void arrivalGoalsCannotCountEvents() {
        List<String> issues = new ArrayList<>();
        PersonaDefinition persona = Personas.parse(ID, json("""
                { "arrives": [ { "text": "t", "event": "townstead:work_completed" } ] }
                """), Map.of(), issues);
        assertNull(persona);
        assertTrue(issues.stream().anyMatch(i -> i.contains("not an event count")), issues.toString());
    }

    @Test
    void badValuesAreReportedPlainly() {
        List<String> issues = new ArrayList<>();
        PersonaDefinition persona = Personas.parse(ID, json("""
                { "arrival": "teleport", "villager": { "gender": "robot" } }
                """), Map.of(), issues);
        assertNull(persona);
        assertTrue(issues.contains("error: arrival must be \"walk_in\" or \"appear\""), issues.toString());
        assertTrue(issues.contains("error: villager.gender must be \"male\" or \"female\""), issues.toString());
    }

    @Test
    void unknownArrivalGoalNamesAreReported() {
        List<String> issues = new ArrayList<>();
        assertNull(Personas.parse(ID, json("{ \"arrives\": \"good_standin\" }"), Map.of(), issues));
        assertTrue(issues.stream().anyMatch(i -> i.contains("good_standin")), issues.toString());
    }

    @Test
    void rollsKeepValuesProfessionsAndInkVariables() {
        List<String> issues = new ArrayList<>();
        PersonaDefinition persona = Personas.parse(ID, json("""
                { "rolls": { "hometown": [
                    { "value": "mill", "profession": "baker", "weight": 3,
                      "vars": { "gift": "minecraft:bread", "cups": 2, "kind": true } },
                    { "value": "mine" } ] } }
                """), Map.of(), issues);
        assertNotNull(persona, issues.toString());
        PersonaRoll roll = persona.rolls().get(0);
        assertEquals("hometown", roll.name());
        PersonaRoll.Option mill = roll.option("mill");
        assertNotNull(mill);
        assertEquals(3, mill.weight());
        assertEquals(ResourceLocation.tryParse("minecraft:baker"), mill.profession());
        assertEquals("minecraft:bread", mill.vars().get("gift"));
        assertEquals(2, mill.vars().get("cups"));
        assertEquals(true, mill.vars().get("kind"));
        assertEquals(1, roll.option("mine").weight());
    }

    @Test
    void rollOptionsNeedAValue() {
        List<String> issues = new ArrayList<>();
        assertNull(Personas.parse(ID, json("{ \"rolls\": { \"hometown\": [ { \"weight\": 2 } ] } }"), Map.of(), issues));
        assertTrue(issues.stream().anyMatch(i -> i.contains("rolls.hometown[0]: each option needs a \"value\"")), issues.toString());
    }

    @Test
    void giftsReadResponsesDefaultsAndFirstTime() {
        List<String> issues = new ArrayList<>();
        PersonaDefinition persona = Personas.parse(ID, json("""
                { "gifts": [
                    { "items": ["$gift", "#minecraft:logs"], "response": "loves", "knot": "loved",
                      "relationship": { "affection": 5 },
                      "first": { "knot": "from_home", "relationship": { "affection": 10 } } },
                    { "items": "minecraft:rotten_flesh", "response": "dislikes" } ] }
                """), Map.of(), issues);
        assertNotNull(persona, issues.toString());
        PersonaGift loved = persona.gifts().get(0);
        assertEquals(PersonaGift.Response.LOVES, loved.response());
        assertEquals(30, loved.satisfaction());
        assertEquals(5f, loved.relationship().get("affection"));
        assertEquals(List.of("loved", "from_home"), loved.knots());
        assertEquals(10f, loved.first().relationship().get("affection"));
        assertEquals(-15, persona.gifts().get(1).satisfaction());
    }

    @Test
    void giftResponsesAreChecked() {
        List<String> issues = new ArrayList<>();
        assertNull(Personas.parse(ID, json("{ \"gifts\": [ { \"items\": \"minecraft:stick\", \"response\": \"adores\" } ] }"), Map.of(), issues));
        assertTrue(issues.contains("error: gifts[0].response must be \"loves\", \"likes\" or \"dislikes\""), issues.toString());
    }
}
