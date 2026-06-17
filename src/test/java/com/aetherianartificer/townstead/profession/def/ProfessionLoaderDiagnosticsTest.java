package com.aetherianartificer.townstead.profession.def;

import com.aetherianartificer.townstead.pheno.lang.compile.Diagnostic;
import com.aetherianartificer.townstead.pheno.lang.compile.Diagnostics;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfessionLoaderDiagnosticsTest {

    private static JsonObject obj(String singleQuoted) {
        return JsonParser.parseString(singleQuoted.replace('\'', '"')).getAsJsonObject();
    }

    private static ResourceLocation rl(String s) {
        return ResourceLocation.tryParse(s);
    }

    private static boolean has(Diagnostics diag, String jsonPath, String fragment) {
        for (Diagnostic d : diag.all()) {
            if (d.jsonPath().equals(jsonPath) && d.message().contains(fragment)) return true;
        }
        return false;
    }

    @Test
    void unknownUnlockModelWarns() {
        Diagnostics diag = new Diagnostics();
        ProfessionDataLoader.parseProfession(rl("t:p"),
                obj("{ 'unlock_model':'bogus' }"), Map.of(), diag);
        assertTrue(has(diag, "$.unlock_model", "Unknown unlock_model"));
    }

    @Test
    void unknownRetrainingWarns() {
        Diagnostics diag = new Diagnostics();
        ProfessionDataLoader.parseProfession(rl("t:p"),
                obj("{ 'retraining':'sometimes' }"), Map.of(), diag);
        assertTrue(has(diag, "$.retraining", "Unknown retraining"));
    }

    @Test
    void skillMissingProfessionErrorsAndReturnsNull() {
        Diagnostics diag = new Diagnostics();
        SkillDef skill = ProfessionDataLoader.parseSkill(rl("t:s"), obj("{ 'tier':1 }"), Map.of(), diag);
        assertNull(skill, "a skill without a valid profession id must be dropped");
        assertTrue(has(diag, "$.profession", "profession"));
    }

    @Test
    void unknownGrantOpWarns() {
        Diagnostics diag = new Diagnostics();
        SkillDef skill = ProfessionDataLoader.parseSkill(rl("t:s"),
                obj("{ 'profession':'t:p', 'grants':[ { 'capability':'t:cap', 'op':'frobnicate', 'value':1 } ] }"),
                Map.of(), diag);
        assertNotNull(skill);
        assertTrue(has(diag, "$.grants[0].op", "Unknown op"));
    }
}
