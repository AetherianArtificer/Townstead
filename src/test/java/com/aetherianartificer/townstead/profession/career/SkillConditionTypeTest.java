package com.aetherianartificer.townstead.profession.career;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class SkillConditionTypeTest {

    @Test
    void requiresAnExactSkillResourceId() {
        SkillConditionType type = new SkillConditionType();
        assertNotNull(type.parse(JsonParser.parseString(
                "{\"skill\":\"example:beekeeper/gentle_hands\"}").getAsJsonObject()));
        assertNull(type.parse(JsonParser.parseString("{}").getAsJsonObject()));
        assertNull(type.parse(JsonParser.parseString(
                "{\"skill\":\"not a resource id\"}").getAsJsonObject()));
    }
}
