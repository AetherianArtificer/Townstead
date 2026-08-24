package com.aetherianartificer.townstead.chronicle.pregen;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChronicleWorkHistoryTest {

    @Test
    void parsesProfessionWorkRates() {
        ResourceLocation id = ResourceLocation.tryParse("test:butcher");
        ChronicleWorkHistory history = ChronicleWorkHistory.parse(id, JsonParser.parseString("""
                {
                  "schema": "townstead:chronicle_work_history/v1",
                  "profession": "minecraft:butcher",
                  "per_year": {
                    "townstead:butchery_carcass": 90,
                    "test:ignored": 0
                  }
                }
                """).getAsJsonObject());

        assertEquals("minecraft:butcher", history.profession());
        assertEquals(Map.of("townstead:butchery_carcass", 90), history.perYear());
    }

    @Test
    void rejectsTheRetiredCompetenceSchema() {
        ResourceLocation id = ResourceLocation.tryParse("test:old_name");
        assertThrows(IllegalArgumentException.class, () -> ChronicleWorkHistory.parse(
                id, JsonParser.parseString("""
                        {"schema":"townstead:competence/v1"}
                        """).getAsJsonObject()));
    }
}
