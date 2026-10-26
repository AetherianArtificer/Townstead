package com.aetherianartificer.townstead.pheno.condition;

import com.aetherianartificer.townstead.pheno.condition.types.ExistsConditionType;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExistsConditionTest {
    @Test void parsesBlocksItemsAndTagsAndRejectsTheRest() {
        ConditionTypes.register(new ExistsConditionType());
        assertNotNull(Conditions.parse(JsonParser.parseString("""
                {"type":"pheno:exists","block":"farmersdelight:rich_soil_farmland"}
                """)));
        assertNotNull(Conditions.parse(JsonParser.parseString("""
                {"type":"pheno:exists","item":["#c:fertilizers","minecraft:bone_meal"]}
                """)));
        assertNull(Conditions.parse(JsonParser.parseString("""
                {"type":"pheno:exists"}
                """)));
        assertNull(Conditions.parse(JsonParser.parseString("""
                {"type":"pheno:exists","block":"not an id"}
                """)));
    }
}
