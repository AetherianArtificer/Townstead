package com.aetherianartificer.townstead.work.recipe;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeProjectionMergeTest {

    /** A recipe that hands out its inputs one getter at a time, the way LSO's sewing recipe does. */
    public static final class TwoGetterRecipe {
        public String getBase() { return "string"; }
        public List<String> getAddition() { return List.of("fern_leaf"); }
        public String getNothing() { return null; }
    }

    @Test
    void mergeConcatenatesEveryPathInOrder() {
        RecipeProjectionAccess.Accessor accessor = RecipeProjectionAccess.parse(JsonParser.parseString(
                "{ \"merge\": [\"getBase\", \"getAddition\"], \"operation\": \"list\", \"required\": true }"));
        assertEquals(List.of("getBase", "getAddition"), accessor.merge());
        assertTrue(accessor.aliases().isEmpty());

        RecipeProjectionAccess.Read read = RecipeProjectionAccess.read(new TwoGetterRecipe(), accessor);
        assertTrue(read.found());
        assertEquals("<merge>", read.selectedAlias());
        assertEquals(List.of("string", "fern_leaf"), read.value());
    }

    @Test
    void mergeSkipsPathsThatFailAndReportsThem() {
        RecipeProjectionAccess.Accessor accessor = RecipeProjectionAccess.parse(JsonParser.parseString(
                "{ \"merge\": [\"getNothing\", \"getMissing\", \"getBase\"], \"operation\": \"list\" }"));
        RecipeProjectionAccess.Read read = RecipeProjectionAccess.read(new TwoGetterRecipe(), accessor);
        assertTrue(read.found());
        assertEquals(List.of("string"), read.value());
        assertFalse(read.failures().isEmpty());
    }

    @Test
    void mergeWithNothingResolvedFallsThroughToDefaultOrNotFound() {
        RecipeProjectionAccess.Accessor none = RecipeProjectionAccess.parse(JsonParser.parseString(
                "{ \"merge\": [\"getMissing\"], \"operation\": \"list\" }"));
        assertFalse(RecipeProjectionAccess.read(new TwoGetterRecipe(), none).found());

        RecipeProjectionAccess.Accessor withDefault = RecipeProjectionAccess.parse(JsonParser.parseString(
                "{ \"merge\": [\"getMissing\"], \"operation\": \"list\", \"default\": [] }"));
        assertTrue(RecipeProjectionAccess.read(new TwoGetterRecipe(), withDefault).found());
    }

    @Test
    void accessorStillNeedsSomethingToRead() {
        assertThrows(IllegalArgumentException.class, () -> RecipeProjectionAccess.parse(JsonParser.parseString(
                "{ \"operation\": \"list\" }")));
        assertThrows(IllegalArgumentException.class, () -> RecipeProjectionAccess.parse(JsonParser.parseString(
                "{ \"merge\": \"getBase\" }")));
    }
}
