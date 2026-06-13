package com.aetherianartificer.townstead.pheno.lang.validate;

import com.aetherianartificer.townstead.pheno.lang.compile.Diagnostic;
import com.aetherianartificer.townstead.pheno.lang.compile.Diagnostics;
import com.aetherianartificer.townstead.pheno.lang.schema.PhenoSchemas;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhenoValidatorTest {

    @BeforeAll
    static void registerSchemas() {
        PhenoSchemas.registerAll();
    }

    private static JsonObject obj(String singleQuoted) {
        return JsonParser.parseString(singleQuoted.replace('\'', '"')).getAsJsonObject();
    }

    private static Diagnostics validate(String json) {
        Diagnostics diag = new Diagnostics();
        PhenoValidator.validateGene(ResourceLocation.tryParse("townstead_test:g"), obj(json), diag);
        return diag;
    }

    private static boolean has(Diagnostics diag, String jsonPath, String messageFragment) {
        for (Diagnostic d : diag.all()) {
            if (d.jsonPath().equals(jsonPath) && d.message().contains(messageFragment)) return true;
        }
        return false;
    }

    @Test
    void missingRequiredChildIsFlaggedWithExactPath() {
        Diagnostics diag = validate("{ 'type':'pheno:active_ability', 'cooldown':40 }");
        assertTrue(has(diag, "$.action", "Missing required field"),
                "active_ability without 'action' must be flagged at $.action");
    }

    @Test
    void scalarTypeMismatchIsFlaggedWithExactPath() {
        Diagnostics diag = validate("{ 'type':'pheno:attribute', 'attribute':5 }");
        assertTrue(has(diag, "$.attribute", "resource id"),
                "a numeric attribute id must be flagged at $.attribute");
    }

    @Test
    void variantMissingRequiredFieldUsesVariantPath() {
        Diagnostics diag = validate(
                "{ 'type':'pheno:active_ability', 'variants':{ 'q':{ 'cooldown':40 } } }");
        assertTrue(has(diag, "$.variants.q.action", "Missing required field"),
                "a required field missing inside a variant must be located under that variant");
    }

    @Test
    void inlineResourceFieldMismatchIsFlaggedUnderItsName() {
        Diagnostics diag = validate("{ 'type':'pheno:trigger', 'trigger':'press', 'key':'jump',"
                + " 'action':{'type':'townstead_origins:heal','amount':1},"
                + " 'resources':{ 'jumps':{ 'max':'lots' } } }");
        assertTrue(has(diag, "$.resources.jumps.max", "number"),
                "a non-numeric resource max must be flagged at $.resources.jumps.max");
    }

    @Test
    void resourcesMustBeAnObjectOfMeters() {
        Diagnostics diag = validate("{ 'type':'pheno:trigger', 'trigger':'press', 'key':'jump',"
                + " 'action':{'type':'townstead_origins:heal','amount':1}, 'resources':[ 'jumps' ] }");
        assertTrue(has(diag, "$.resources", "object of named resource meters"),
                "a non-object resources block must be flagged at $.resources");
    }

    @Test
    void wellFormedInlineResourceHasNoFieldDiagnostics() {
        // Type-resolution depends on the live registry (empty in unit tests), so only field-shape
        // diagnostics are meaningful here, mirroring wellFormedGeneHasNoFieldDiagnostics.
        Diagnostics diag = validate("{ 'type':'pheno:trigger', 'trigger':'press', 'key':'jump',"
                + " 'action':{'type':'townstead_origins:heal','amount':1},"
                + " 'resources':{ 'jumps':{ 'min':0, 'max':3, 'start':0 } } }");
        for (Diagnostic d : diag.all()) {
            if (!d.jsonPath().startsWith("$.resources")) continue;
            assertFalse(d.message().contains("Missing required field") || d.message().contains("Expected"),
                    "well-formed inline resource should produce no field diagnostics, got: " + d.render());
        }
    }

    @Test
    void wellFormedGeneHasNoFieldDiagnostics() {
        Diagnostics diag = validate(
                "{ 'type':'pheno:active_ability',"
                        + " 'action':{'type':'townstead_origins:heal','amount':1}, 'cooldown':40 }");
        for (Diagnostic d : diag.all()) {
            assertFalse(d.message().contains("Missing required field") || d.message().contains("Expected"),
                    "well-formed gene should produce no field diagnostics, got: " + d.render());
        }
    }
}
