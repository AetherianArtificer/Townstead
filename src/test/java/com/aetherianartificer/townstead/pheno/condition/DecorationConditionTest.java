package com.aetherianartificer.townstead.pheno.condition;

import com.aetherianartificer.townstead.pheno.condition.types.NearDecorationConditionType;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DecorationConditionTest {
    @Test void parsesDecorationSelectorsAndRejectsInvalidIds() {
        ConditionTypes.register(new NearDecorationConditionType());
        assertNotNull(Conditions.parse(JsonParser.parseString("""
                {"type":"pheno:near_decoration","decoration":"townstead:hearth","radius":4}
                """)));
        assertNotNull(Conditions.parse(JsonParser.parseString("""
                {"type":"pheno:near_decoration","radius":4}
                """)));
        assertNull(Conditions.parse(JsonParser.parseString("""
                {"type":"pheno:near_decoration","decoration":"not an identifier"}
                """)));
    }
}
