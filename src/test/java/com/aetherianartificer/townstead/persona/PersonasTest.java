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
}
