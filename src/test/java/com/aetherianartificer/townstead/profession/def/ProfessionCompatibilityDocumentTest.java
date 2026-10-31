package com.aetherianartificer.townstead.profession.def;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProfessionCompatibilityDocumentTest {

    private static final ResourceLocation COOK = id("townstead:cook");
    private static final ResourceLocation EXTERNAL_COOK = id("chefsdelight:cook");
    private static final ResourceLocation EXTERNAL_CHEF = id("chefsdelight:chef");

    @Test
    void rootAliasesAndPathAliasesHaveDifferentMeanings() {
        Map<ResourceLocation, ProfessionDefs.Resolution> mappings =
                ProfessionCompatibilityDocument.parse(COOK, object("""
                        {
                          "schema":"townstead:profession_compatibility/v1",
                          "aliases":["chefsdelight:cook"],
                          "path_aliases":{"chefsdelight:chef":"chef"}
                        }
                        """), Set.of("chef"));

        assertEquals(COOK, mappings.get(EXTERNAL_COOK).professionId());
        assertNull(mappings.get(EXTERNAL_COOK).pathId());
        assertEquals(new ProfessionDefs.Resolution(COOK, "chef"),
                mappings.get(EXTERNAL_CHEF));
    }

    @Test
    void pathAliasMustNameAPathThatActuallyLoaded() {
        assertThrows(IllegalArgumentException.class, () ->
                ProfessionCompatibilityDocument.parse(COOK, object("""
                        {"schema":"townstead:profession_compatibility/v1",
                         "path_aliases":{"chefsdelight:chef":"chef"}}
                        """), Set.of()));
    }

    private static com.google.gson.JsonObject object(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    private static ResourceLocation id(String raw) {
        return ResourceLocation.tryParse(raw);
    }
}
