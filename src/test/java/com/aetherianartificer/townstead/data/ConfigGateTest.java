package com.aetherianartificer.townstead.data;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConfigGateTest {

    @Test
    void comparesASectionedBooleanWithoutKnowingTheOwningMod() {
        var gate = JsonParser.parseString("""
                {"file":"Butchery.toml",
                 "path":["Farmers Delight Compatibility","Farmers Delight"],
                 "equals":true}
                """);
        assertEquals(Boolean.TRUE, ConfigGate.evaluate(gate, path -> {
            assertEquals(List.of("Farmers Delight Compatibility", "Farmers Delight"), path);
            return true;
        }));
        assertEquals(Boolean.FALSE, ConfigGate.evaluate(gate, path -> false));
        assertEquals(Boolean.FALSE, ConfigGate.evaluate(gate, path -> null));
    }

    @Test
    void malformedGatesFailClosedAtParseTime() {
        assertNull(ConfigGate.evaluate(JsonParser.parseString("{\"equals\":true}"), path -> true));
        assertNull(ConfigGate.evaluate(JsonParser.parseString("{\"path\":[],\"equals\":true}"), path -> true));
    }

    @Test
    void authoredDefaultCoversAnAbsentFileOrKey() {
        var gate = JsonParser.parseString("""
                {"file":"Butchery.toml","path":["General","Organs"],
                 "equals":true,"default":true}
                """);
        assertEquals(Boolean.TRUE, ConfigGate.evaluate(gate, path -> null));
        assertEquals(Boolean.FALSE, ConfigGate.evaluate(gate, path -> false));
    }
}
