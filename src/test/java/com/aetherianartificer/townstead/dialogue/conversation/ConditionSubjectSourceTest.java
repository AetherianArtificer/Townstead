package com.aetherianartificer.townstead.dialogue.conversation;

import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.aetherianartificer.townstead.pheno.condition.ConditionTypes;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConditionSubjectSourceTest {

    @BeforeAll
    static void register() {
        ConditionTypes.register(new ConditionType() {
            @Override public String key() { return "test:subject_always"; }
            @Override public Condition parse(JsonObject json) { return ctx -> true; }
        });
    }

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    @Test
    void readsBothSidesUrgencyAndValence() {
        var parsed = ConditionSubjectSource.parse(json("""
                {"type":"townstead:condition",
                 "speaker":{"type":"test:subject_always"},
                 "listener":{"type":"test:subject_always"},
                 "urgency":[{"when":{"type":"test:subject_always"},"value":0.7},{"value":0.4}],
                 "valence":"negative"}
                """));
        assertNotNull(parsed.speaker());
        assertNotNull(parsed.listener());
        assertEquals(2, parsed.urgency().size());
        assertNull(parsed.urgency().get(1).when());
        assertEquals("negative", parsed.valence());
    }

    @Test
    void oneSideIsEnoughAndValenceDefaultsToNeutral() {
        var parsed = ConditionSubjectSource.parse(json("{\"type\":\"townstead:condition\",\"listener\":{\"type\":\"test:subject_always\"}}"));
        assertNull(parsed.speaker());
        assertEquals("neutral", parsed.valence());
    }

    @Test
    void rejectsWhatCannotWork() {
        assertThrows(IllegalArgumentException.class, () -> ConditionSubjectSource.parse(json("{\"type\":\"townstead:condition\"}")));
        assertThrows(IllegalArgumentException.class, () -> ConditionSubjectSource.parse(json(
                "{\"type\":\"townstead:condition\",\"speaker\":{\"type\":\"test:no_such_condition\"}}")));
        assertThrows(IllegalArgumentException.class, () -> ConditionSubjectSource.parse(json(
                "{\"type\":\"townstead:condition\",\"speaker\":{\"type\":\"test:subject_always\"},\"valence\":\"sad\"}")));
        assertThrows(IllegalArgumentException.class, () -> ConditionSubjectSource.parse(json(
                "{\"type\":\"townstead:condition\",\"speaker\":{\"type\":\"test:subject_always\"},\"urgency\":[{\"value\":2}]}")));
        assertThrows(IllegalArgumentException.class, () -> ConditionSubjectSource.parse(json(
                "{\"type\":\"townstead:condition\",\"speaker\":{\"type\":\"test:subject_always\"},\"who\":1}")));
    }
}
