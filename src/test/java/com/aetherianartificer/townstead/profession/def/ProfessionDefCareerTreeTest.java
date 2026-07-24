package com.aetherianartificer.townstead.profession.def;

import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.aetherianartificer.townstead.pheno.condition.ConditionTypes;
import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.aetherianartificer.townstead.pheno.lang.compile.Diagnostics;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The unified schema: gated professions are ordinary defs with pheno-condition requirements,
 * acquisition routes, and job-site providers. No separate advanced-class schema, no parent
 * graph — careers are flat.
 */
class ProfessionDefCareerTreeTest {

    private static boolean flag;

    @BeforeAll
    static void registerStubCondition() {
        if (ConditionTypes.get("test:flag").isEmpty()) {
            ConditionTypes.register(new ConditionType() {
                @Override public String key() { return "test:flag"; }
                @Override public Condition parse(JsonObject json) { return ctx -> flag; }
            });
        }
    }

    @Test
    void gatedProfessionParsesRoutesAndRequirements() {
        ProfessionDef def = parse("{ 'hidden': true,"
                + " 'requirements': { 'type': 'test:flag' },"
                + " 'acquisition_routes': ['self_discovery', 'mentor'] }");
        assertNotNull(def);
        assertFalse(def.isRoot());
        assertTrue(def.hidden());
        assertEquals(List.of("self_discovery", "mentor"), def.acquisitionRoutes());
        flag = false;
        assertFalse(def.requirements().test(null));
        flag = true;
        assertTrue(def.requirements().test(null));
    }

    @Test
    void practicedProfessionIsRootWithAlwaysRequirements() {
        ProfessionDef def = parse("{ }");
        assertNotNull(def);
        assertTrue(def.isRoot());
        assertSame(Conditions.ALWAYS, def.requirements());
    }

    @Test
    void malformedRequirementsDropTheDefWithDiagnostic() {
        Diagnostics diag = new Diagnostics();
        ProfessionDef def = parse("{ 'requirements': { 'type': 'nonsense:missing' } }", diag);
        assertNull(def, "a broken gate must never read as always eligible");
        assertTrue(diag.all().stream().anyMatch(d -> d.message().contains("requirements")));
    }

    @Test
    void poiProvidersParseIntoTypedJobSites() {
        ProfessionDef def = parse("{ 'poi': ["
                + " { 'type': 'townstead:building', 'type_prefix': 'compat/farmersdelight/kitchen_l' },"
                + " { 'type': 'townstead:job_block', 'block': 'minecraft:composter' },"
                + " { 'type': 'townstead:always' } ] }");
        assertNotNull(def);
        assertEquals(3, def.jobSites().size());
        assertInstanceOf(JobSiteProvider.Building.class, def.jobSites().get(0));
        assertInstanceOf(JobSiteProvider.JobBlock.class, def.jobSites().get(1));
        assertInstanceOf(JobSiteProvider.Always.class, def.jobSites().get(2));
    }

    @Test
    void unknownPoiTypeErrorsButKeepsTheDef() {
        Diagnostics diag = new Diagnostics();
        ProfessionDef def = parse("{ 'poi': [ { 'type': 'nonsense:portal' } ] }", diag);
        assertNotNull(def);
        assertTrue(def.jobSites().isEmpty());
        assertTrue(diag.all().stream().anyMatch(d -> d.message().contains("job-site provider")));
    }

    private static ProfessionDef parse(String singleQuoted) {
        return parse(singleQuoted, new Diagnostics());
    }

    private static ProfessionDef parse(String singleQuoted, Diagnostics diag) {
        JsonObject json = JsonParser.parseString(singleQuoted.replace('\'', '"')).getAsJsonObject();
        json.addProperty("schema", "townstead:profession/v1");
        diag.forResource(ResourceLocation.tryParse("test:career"));
        return ProfessionDataLoader.parseProfession(
                ResourceLocation.tryParse("test:career"), json, Map.of(), diag);
    }
}
