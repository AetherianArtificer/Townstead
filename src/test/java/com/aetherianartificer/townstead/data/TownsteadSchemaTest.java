package com.aetherianartificer.townstead.data;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TownsteadSchemaTest {
    @Test
    void acceptsLegacyDocumentsWithoutSchema() {
        assertDoesNotThrow(() -> TownsteadSchema.validate(
                JsonParser.parseString("{}").getAsJsonObject(),
                "townstead:calendar/v1"));
    }

    @Test
    void acceptsMatchingSchema() {
        assertDoesNotThrow(() -> TownsteadSchema.validate(
                JsonParser.parseString("{\"schema\":\"townstead:calendar/v1\"}").getAsJsonObject(),
                "townstead:calendar/v1"));
    }

    @Test
    void rejectsWrongDocumentSchema() {
        assertThrows(IllegalArgumentException.class, () -> TownsteadSchema.validate(
                JsonParser.parseString("{\"schema\":\"townstead:origin/v1\"}").getAsJsonObject(),
                "townstead:calendar/v1"));
    }
}
