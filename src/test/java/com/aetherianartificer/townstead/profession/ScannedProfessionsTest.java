package com.aetherianartificer.townstead.profession;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The scan's eligibility rules: advanced Townstead defs only, with an explicit opt-out. */
class ScannedProfessionsTest {

    private static JsonObject obj(String singleQuoted) {
        return JsonParser.parseString(singleQuoted.replace('\'', '"')).getAsJsonObject();
    }

    @Test
    void gatedCareerIsEligible() {
        // The mods-gate path is untestable here (ModGate's default predicate needs ModList);
        // ModGateTest covers the grammar via the predicate overload.
        assertTrue(ScannedProfessions.eligible(obj(
                "{ 'schema': 'townstead:profession/v1', 'acquisition_routes': ['mentor'] }")),
                "gated careers (barista, baker) register their own professions");
        assertTrue(ScannedProfessions.eligible(obj(
                "{ 'schema': 'townstead:profession/v2', 'acquisition_routes': ['mentor'] }")),
                "dir-layout v2 defs register too");
    }

    @Test
    void practicedCareerIsNotEligible() {
        assertFalse(ScannedProfessions.eligible(obj(
                "{ 'schema': 'townstead:profession/v1' }")),
                "practiced careers extend professions that already exist");
        assertFalse(ScannedProfessions.eligible(obj(
                "{ 'schema': 'townstead:profession/v2', 'acquisition_routes': [] }")));
        assertFalse(ScannedProfessions.eligible(obj(
                "{ 'schema': 'townstead:profession/v1', 'parents': ['townstead:cook'] }")),
                "parents is dead schema; it neither gates nor grants registration");
    }

    @Test
    void foreignProfessionFolderIsNotEligible() {
        assertFalse(ScannedProfessions.eligible(obj(
                "{ 'acquisition_routes': ['mentor'] }")),
                "another mod's profession/ data folder must not be swept in");
        assertFalse(ScannedProfessions.eligible(obj(
                "{ 'schema': 'othermod:profession/v9', 'acquisition_routes': ['mentor'] }")));
    }

    @Test
    void jobBlocksComeFromJobBlockProvidersOnly() {
        var blocks = ScannedProfessions.jobBlocks(obj(
                "{ 'poi': [ { 'type': 'townstead:job_block', 'block': 'minecraft:lectern' },"
                        + " { 'type': 'townstead:job_block', 'blocks': ['minecraft:loom', 'minecraft:barrel'] },"
                        + " { 'type': 'townstead:building', 'type_prefix': 'compat/x/lab_l' } ] }"));
        assertTrue(blocks.contains(net.minecraft.resources.ResourceLocation.tryParse("minecraft:lectern")));
        assertTrue(blocks.contains(net.minecraft.resources.ResourceLocation.tryParse("minecraft:loom")));
        assertTrue(blocks.contains(net.minecraft.resources.ResourceLocation.tryParse("minecraft:barrel")));
        assertTrue(blocks.size() == 3, "building providers contribute no job blocks");
    }

    @Test
    void optOutIsRespected() {
        assertFalse(ScannedProfessions.eligible(obj(
                "{ 'schema': 'townstead:profession/v1', 'parents': ['townstead:cook'],"
                        + " 'register_profession': false }")));
    }
}
