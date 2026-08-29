package com.aetherianartificer.townstead.pheno;

import com.aetherianartificer.townstead.pheno.selector.types.ColumnBlockSelectorType;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ColumnBlockSelectorTypeTest {
    @Test
    void acceptsAFilteredBoundedColumn() {
        assertNotNull(new ColumnBlockSelectorType().parse(JsonParser.parseString("""
                {"type":"pheno:column","direction":"down","distance":5,"limit":1,
                 "where":{"type":"pheno:in_tag","tag":"minecraft:campfires"}}
                """).getAsJsonObject()));
    }

    @Test
    void rejectsUnknownDirectionsAndUnboundedDistances() {
        assertNull(new ColumnBlockSelectorType().parse(JsonParser.parseString(
                "{\"direction\":\"sideways\",\"distance\":5}").getAsJsonObject()));
        assertNull(new ColumnBlockSelectorType().parse(JsonParser.parseString(
                "{\"direction\":\"down\",\"distance\":65}").getAsJsonObject()));
    }
}
