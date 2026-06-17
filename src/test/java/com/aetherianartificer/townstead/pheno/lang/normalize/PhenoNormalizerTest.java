package com.aetherianartificer.townstead.pheno.lang.normalize;

import com.aetherianartificer.townstead.pheno.lang.schema.PhenoSchemas;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhenoNormalizerTest {

    @BeforeAll
    static void registerSchemas() {
        PhenoSchemas.registerAll();
    }

    private static JsonObject obj(String singleQuoted) {
        return JsonParser.parseString(singleQuoted.replace('\'', '"')).getAsJsonObject();
    }

    @Test
    void geneEnvelopeAndTriggerAndWithLower() {
        JsonObject out = PhenoNormalizer.normalize(obj(
                "{ 'schema':'townstead:gene/v2', 'gene':{'dominance':'recessive','category':'Combat'},"
                        + " 'on':'hurt', 'do':{'type':'pheno:with','target':'attacker',"
                        + " 'do':{'type':'pheno:damage','amount':2}} }"));
        assertEquals("recessive", out.get("dominance").getAsString());
        assertEquals("pheno:trigger", out.get("type").getAsString());
        assertEquals("when_hurt", out.get("trigger").getAsString());
        JsonObject action = out.getAsJsonObject("action");
        assertEquals("pheno:target_action", action.get("type").getAsString());
        assertEquals("pheno:damage", action.getAsJsonObject("action").get("type").getAsString());
    }

    @Test
    void pressTriggerSugarLifts() {
        JsonObject out = PhenoNormalizer.normalize(obj(
                "{ 'schema':'townstead:gene/v2', 'gene':{'category':'ability'},"
                        + " 'on':{'press':'jump'}, 'do':{'type':'pheno:jump'} }"));
        assertEquals("pheno:trigger", out.get("type").getAsString());
        assertEquals("press", out.get("trigger").getAsString());
        assertEquals("jump", out.get("key").getAsString());
        assertEquals("pheno:jump", out.getAsJsonObject("action").get("type").getAsString());
    }

    @Test
    void durationUnitConverts() {
        JsonObject out = PhenoNormalizer.normalize(obj(
                "{ 'schema':'townstead:gene/v2', 'type':'pheno:active_ability',"
                        + " 'action':{'type':'pheno:heal','amount':1}, 'cooldown':'2s' }"));
        assertEquals(40, out.get("cooldown").getAsInt());
    }

    @Test
    void malformedUnitIsPreservedNotZeroed() {
        JsonObject out = PhenoNormalizer.normalize(obj(
                "{ 'schema':'townstead:gene/v2', 'type':'pheno:active_ability',"
                        + " 'action':{'type':'pheno:heal','amount':1}, 'cooldown':'tomorrowish' }"));
        assertTrue(out.get("cooldown").getAsJsonPrimitive().isString());
        assertEquals("tomorrowish", out.get("cooldown").getAsString());
    }

    @Test
    void variantConfigsAreNormalized() {
        JsonObject out = PhenoNormalizer.normalize(obj(
                "{ 'schema':'townstead:gene/v2', 'type':'pheno:active_ability', 'variants':{"
                        + " 'q':{ 'action':{'type':'pheno:heal','amount':1}, 'cooldown':'3s' } } }"));
        JsonObject variant = out.getAsJsonObject("variants").getAsJsonObject("q");
        assertEquals(60, variant.get("cooldown").getAsInt());
    }

    @Test
    void variantNestedDoAndWhenShorthandAreLowered() {
        JsonObject out = PhenoNormalizer.normalize(obj(
                "{ 'schema':'townstead:gene/v2', 'type':'pheno:active_ability', 'variants':{"
                        + " 'q':{ 'do':{'type':'pheno:heal','amount':1}, 'when':{'type':'pheno:on_fire'},"
                        + " 'cooldown':'3s' } } }"));
        JsonObject variant = out.getAsJsonObject("variants").getAsJsonObject("q");
        // do -> the gene's primary child (action); when -> condition; units inside the variant convert.
        assertEquals("pheno:heal", variant.getAsJsonObject("action").get("type").getAsString());
        assertEquals("pheno:on_fire", variant.getAsJsonObject("condition").get("type").getAsString());
        assertEquals(60, variant.get("cooldown").getAsInt());
        assertTrue(!variant.has("do") && !variant.has("when"));
    }

    @Test
    void v1ResourcesAreReturnedUntouched() {
        JsonObject in = obj("{ 'type':'pheno:active_ability', 'cooldown':'2s' }");
        JsonObject out = PhenoNormalizer.normalize(in);
        assertSame(in, out);
        assertEquals("2s", out.get("cooldown").getAsString());
    }

    @Test
    void legacyVersionFieldStillSelectsV2() {
        JsonObject out = PhenoNormalizer.normalize(obj(
                "{ 'pheno_version':2, 'type':'pheno:active_ability',"
                        + " 'action':{'type':'pheno:heal','amount':1}, 'cooldown':'2s' }"));
        assertEquals(40, out.get("cooldown").getAsInt());
    }

    @Test
    void nonexistentV1SchemaIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> PhenoNormalizer.normalize(obj(
                "{ 'schema':'townstead:gene/v1', 'pheno_version':2,"
                        + " 'type':'pheno:active_ability', 'cooldown':'2s' }")));
    }

    @Test
    void unknownSchemaIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> PhenoNormalizer.normalize(obj(
                "{ 'schema':'townstead:pheno/v99', 'type':'pheno:active_ability' }")));
    }
}
