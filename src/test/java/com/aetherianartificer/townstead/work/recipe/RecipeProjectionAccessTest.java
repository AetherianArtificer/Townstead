package com.aetherianartificer.townstead.work.recipe;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeProjectionAccessTest {
    private record Payload(int ticks, ResourceLocation output) {}
    private static final class ForeignRecipe {
        private final Payload renamed = new Payload(480, id("test:tea"));
        public List<String> getIngredients() { return List.of("leaf", "water"); }
    }

    @AfterEach
    void reset() { RecipeProjections.replaceAll(List.of()); }

    @Test
    void accessorUsesVersionAliasesAndRecordsFailedRawPaths() {
        var accessor = RecipeProjectionAccess.parse(JsonParser.parseString("""
                {"aliases":["oldPayload.ticks","renamed.ticks"],"operation":"integer","required":true}
                """));
        RecipeProjectionAccess.Read read = RecipeProjectionAccess.read(new ForeignRecipe(), accessor);

        assertTrue(read.found());
        assertEquals(480, read.value());
        assertEquals(480, read.rawValue());
        assertEquals("renamed.ticks", read.selectedAlias());
        assertTrue(read.failures().get(0).contains("oldPayload"));
    }

    @Test
    void projectionPreservesSemanticValuesProvenanceAndFailureDiagnostics() {
        ResourceLocation definitionId = id("test:foreign_recipe");
        var json = JsonParser.parseString("""
                {
                  "schema":"townstead:recipe_projection/v1",
                  "recipe_types":["test:v2_recipe", "test:v1_recipe"],
                  "domain":"brewing.tea",
                  "fields":{
                    "inputs":{"path":"ingredients","operation":"list","required":true},
                    "time":{"aliases":["oldTicks","renamed.ticks"],"operation":"integer","required":true},
                    "output":{"path":"renamed.output","operation":"resource_id","required":true},
                    "readiness":{"default":true,"operation":"boolean"}
                  }
                }
                """).getAsJsonObject();
        RecipeProjections.Definition definition = RecipeProjections.parse(
                definitionId, json, "data/test/recipe_projection/foreign_recipe.json");
        RecipeProjections.replaceAll(List.of(definition));

        RecipeProjections.View view = RecipeProjections.project(
                id("test:tea_recipe"), id("test:v2_recipe"), new ForeignRecipe());

        assertTrue(view.succeeded(), view::failureSummary);
        assertEquals("brewing.tea", view.domain());
        assertEquals(480, view.intValue("time", 0));
        assertEquals(id("test:tea"), view.idValue("output"));
        assertEquals(List.of("leaf", "water"), view.listValue("inputs"));
        assertEquals(definitionId, view.provenance().definition());
        assertEquals("renamed.ticks", view.provenance().selectedAliases().get("time"));
        assertTrue(view.rawFields().get("time") instanceof Integer);
        assertFalse(view.rawFieldFailures().get("time").isEmpty());
    }

    @Test
    void requiredMissAndFalseReadinessFailClosed() {
        RecipeProjections.Definition missing = RecipeProjections.parse(id("test:missing"),
                JsonParser.parseString("""
                    {"schema":"townstead:recipe_projection/v1","recipe_types":["test:recipe"],
                     "fields":{"output":{"path":"gone","operation":"item_id","required":true}}}
                    """).getAsJsonObject(), "test");
        RecipeProjections.replaceAll(List.of(missing));
        RecipeProjections.View miss = RecipeProjections.project(
                id("test:id"), id("test:recipe"), new ForeignRecipe());
        assertFalse(miss.succeeded());
        assertEquals(RecipeProjections.FailureKind.REQUIRED_FIELD_MISSING,
                miss.diagnostics().get(0).kind());

        RecipeProjections.Definition notReady = RecipeProjections.parse(id("test:not_ready"),
                JsonParser.parseString("""
                    {"schema":"townstead:recipe_projection/v1","recipe_types":["test:recipe"],
                     "fields":{"readiness":{"default":false,"operation":"boolean"}}}
                    """).getAsJsonObject(), "test");
        RecipeProjections.replaceAll(List.of(notReady));
        RecipeProjections.View unavailable = RecipeProjections.project(
                id("test:id"), id("test:recipe"), new ForeignRecipe());
        assertFalse(unavailable.succeeded());
        assertEquals(RecipeProjections.FailureKind.READINESS_FALSE,
                unavailable.diagnostics().get(0).kind());
    }

    @Test
    void malformedProjectionVocabularyIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> RecipeProjections.parse(id("test:bad"),
                JsonParser.parseString("""
                    {"schema":"townstead:recipe_projection/v1","recipe_types":["test:recipe"],
                     "fields":{"alcohol":{"path":"strength","operation":"number"}}}
                    """).getAsJsonObject(), "test"));
        assertThrows(IllegalArgumentException.class, () -> RecipeProjectionAccess.parse(
                JsonParser.parseString("{\"path\":\"value\",\"operation\":\"guess\"}")));
    }

    private static ResourceLocation id(String raw) { return ResourceLocation.tryParse(raw); }
}
