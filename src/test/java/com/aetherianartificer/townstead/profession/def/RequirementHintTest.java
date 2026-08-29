package com.aetherianartificer.townstead.profession.def;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Display hints mirror the requirements tree without ever becoming the authority. */
class RequirementHintTest {

    @Test
    void extractsKnownLeavesThroughComposites() {
        List<RequirementHint> hints = RequirementHint.extract(JsonParser.parseString("""
                { "type": "pheno:and", "conditions": [
                    { "type": "pheno:chronicle_count", "key": "townstead_work:cook", "at_least": 25 },
                    { "type": "pheno:or", "conditions": [
                        { "type": "pheno:career_xp", "career": "townstead:cook", "at_least": 110 },
                        { "type": "pheno:profession", "profession": "townstead:cook" } ] } ] }"""));
        assertEquals(3, hints.size());
        assertEquals(new RequirementHint(RequirementHint.KIND_CHRONICLE_COUNT, "townstead_work:cook", 25),
                hints.get(0));
        assertEquals(new RequirementHint(RequirementHint.KIND_CAREER_XP, "townstead:cook", 110),
                hints.get(1));
        assertEquals(RequirementHint.KIND_OTHER, hints.get(2).kind());
    }

    @Test
    void malformedTreesYieldNoHints() {
        assertEquals(List.of(), RequirementHint.extract(null));
        assertEquals(List.of(), RequirementHint.extract(JsonParser.parseString("[1,2]")));
    }
}
