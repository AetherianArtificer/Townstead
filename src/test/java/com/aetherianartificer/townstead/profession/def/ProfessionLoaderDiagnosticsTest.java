package com.aetherianartificer.townstead.profession.def;

import com.aetherianartificer.townstead.pheno.lang.compile.Diagnostic;
import com.aetherianartificer.townstead.pheno.lang.compile.Diagnostics;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfessionLoaderDiagnosticsTest {

    @Test
    void feedbackSidecarsAreNotProfessionDocuments() {
        assertTrue(ProfessionDataLoader.isFeedbackSidecar("beekeeper/feedback"));
        assertTrue(ProfessionDataLoader.isFeedbackSidecar("beekeeper/feedback/no_input"));
        assertFalse(ProfessionDataLoader.isFeedbackSidecar("beekeeper/profession"));
        assertFalse(ProfessionDataLoader.isFeedbackSidecar("beekeeper/path/field_keeper/path"));
    }

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
    void professionCannotDeclareCompletedWorkCounters() {
        Diagnostics diag = new Diagnostics();
        ProfessionDef profession = ProfessionDataLoader.parseProfession(rl("t:p"),
                obj("{ 'history_counters':['t:made_thing'] }"), Map.of(), diag);
        assertNull(profession);
        assertTrue(has(diag, "$.history_counters", "not a profession field"));
    }

    @Test
    void professionOwnsItsWorkSoundMetadata() {
        Diagnostics diag = new Diagnostics();
        ProfessionDef profession = ProfessionDataLoader.parseProfession(rl("minecraft:butcher"),
                obj("{ 'work_sound':'minecraft:entity.villager.work_butcher' }"), Map.of(), diag);
        assertNotNull(profession);
        assertEquals(rl("minecraft:entity.villager.work_butcher"), profession.workSound());
    }

    @Test
    void invalidStoragePreferenceDoesNotEraseProfessionWork() {
        WorkTaskTypes.register(rl("townstead_work:interact"));
        Diagnostics diag = new Diagnostics();
        ProfessionDef profession = ProfessionDataLoader.parseProfession(rl("t:beekeeper"),
                obj("{ 'storage':{'buildings':['t:honey_house']},"
                        + " 'work_tasks':[{'type':'townstead_work:interact'}] }"),
                Map.of(), diag);

        assertNotNull(profession);
        assertEquals(com.aetherianartificer.townstead.storage.StoragePreference.NONE,
                profession.storage());
        assertEquals(1, profession.workTasks().size());
        assertTrue(has(diag, "$.storage", "Invalid profession storage preference"));
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

    /**
     * Registers once and only if absent: a duplicate registration logs through
     * {@code Townstead.LOGGER}, whose class initializer cannot run outside mod loading.
     */
    private static void registerEffectImmunity() {
        if (com.aetherianartificer.townstead.root.gene.GeneTypes.get("pheno:effect_immunity").isEmpty()) {
            com.aetherianartificer.townstead.root.gene.GeneTypes.register(
                    new com.aetherianartificer.townstead.root.gene.types.EffectImmunityGeneType());
        }
    }

    @Test
    void unknownPowerTypeErrorsButKeepsSkill() {
        Diagnostics diag = new Diagnostics();
        SkillDef skill = ProfessionDataLoader.parseSkill(rl("t:s"),
                obj("{ 'profession':'t:p', 'power': { 'type':'nonsense:missing' } }"), Map.of(), diag);
        assertNotNull(skill, "a bad power drops only the power, never the learned-history anchor");
        assertNull(skill.power());
        assertTrue(has(diag, "$.power.type", "Unknown power type"));
    }

    @Test
    void invalidPowerConfigErrorsButKeepsSkill() {
        registerEffectImmunity();
        Diagnostics diag = new Diagnostics();
        SkillDef skill = ProfessionDataLoader.parseSkill(rl("t:s"),
                obj("{ 'profession':'t:p', 'power': { 'type':'pheno:effect_immunity' } }"), Map.of(), diag);
        assertNotNull(skill);
        assertNull(skill.power());
        assertTrue(has(diag, "$.power", "Invalid config"));
    }

    @Test
    void validPowerParsesThroughGeneTypeRegistry() {
        registerEffectImmunity();
        Diagnostics diag = new Diagnostics();
        SkillDef skill = ProfessionDataLoader.parseSkill(rl("t:s"),
                obj("{ 'profession':'t:p', 'power': { 'type':'pheno:effect_immunity',"
                        + " 'effects':['minecraft:poison'] } }"), Map.of(), diag);
        assertNotNull(skill);
        assertNotNull(skill.power());
        assertEquals("pheno:effect_immunity", skill.power().typeKey());
    }

    @Test
    void skillGroupUsesRpgClassVocabulary() {
        Diagnostics diag = new Diagnostics();
        SkillDef skill = ProfessionDataLoader.parseSkill(rl("t:s"),
                obj("{ 'profession':'t:p', 'skill_group':'t:combat_stance' }"), Map.of(), diag);
        assertNotNull(skill);
        assertEquals(rl("t:combat_stance"), skill.skillGroup());
    }

    @Test
    void pathSkillReferencesResolveBesideTheSkillFile() {
        Diagnostics diag = new Diagnostics();
        SkillDef skill = ProfessionDataLoader.parseSkill(
                rl("t:beekeeper/hive_keeper/first_aid"),
                obj("{ 'profession':'t:beekeeper', 'requires':['smoker_use'],"
                        + " 'exclusive_with':['protective_clothing'] }"), Map.of(), diag);

        assertNotNull(skill);
        assertEquals(java.util.List.of(rl("t:beekeeper/hive_keeper/smoker_use")),
                skill.requires());
        assertEquals(java.util.List.of(rl("t:beekeeper/hive_keeper/protective_clothing")),
                skill.exclusiveWith());
    }
}
