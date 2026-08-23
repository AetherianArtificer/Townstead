package com.aetherianartificer.townstead.work.feedback;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProfessionFeedbackDocumentTest {

    @Test
    void parsesProfessionSettingsSidecar() {
        ProfessionFeedbackDocument.Settings settings = ProfessionFeedbackDocument.Settings.parse(
                id("test:beekeeper"), object("""
                        {"schema":"townstead:profession_feedback/v1","interval":80,"range":16}
                        """));

        assertEquals(id("test:beekeeper"), settings.profession());
        assertEquals(80, settings.interval());
        assertEquals(16, settings.range());
    }

    @Test
    void parsesOneFileNamedRule() {
        ProfessionFeedbackDocument.Rule rule = ProfessionFeedbackDocument.Rule.parse(
                id("test:profession/beekeeper/feedback/no_hive"), id("test:beekeeper"),
                "no_hive", object("""
                        {
                          "schema":"townstead:profession_feedback_rule/v1",
                          "trigger":"periodic",
                          "priority":7,
                          "dialogue":{"translate":"dialogue.test.no_hive"},
                          "variants":3
                        }
                        """));

        assertEquals("no_hive", rule.id());
        assertEquals(ProfessionFeedbackDocument.Trigger.PERIODIC, rule.trigger());
        assertEquals("dialogue.test.no_hive", rule.translation());
        assertEquals(3, rule.variants());
        assertNotNull(rule.when());
    }

    @Test
    void separatelyNamedRuleFilesMergeNaturally() {
        ProfessionFeedbackDocument.Settings settings = ProfessionFeedbackDocument.Settings.parse(
                id("test:beekeeper"), object("""
                        {"schema":"townstead:profession_feedback/v1"}
                        """));
        ProfessionFeedbackDocument.Rule first = rule("no_hive", "dialogue.test.no_hive");
        ProfessionFeedbackDocument.Rule second = rule("no_tool", "dialogue.test.no_tool");

        ProfessionFeedbackRegistry.replaceAll(List.of(settings), List.of(first, second));

        ProfessionFeedbackRegistry.Channel channel =
                ProfessionFeedbackRegistry.byProfession(id("test:beekeeper"));
        assertNotNull(channel);
        assertEquals(2, channel.rules().size());
    }

    @Test
    void schemasTranslationAndTriggerAreRequiredToBeValid() {
        assertThrows(IllegalArgumentException.class, () -> ProfessionFeedbackDocument.Settings.parse(
                id("test:beekeeper"), object("{}")));
        assertThrows(IllegalArgumentException.class, () -> ProfessionFeedbackDocument.Rule.parse(
                id("test:source"), id("test:beekeeper"), "bad", object("""
                        {"schema":"townstead:profession_feedback_rule/v1","dialogue":{"text":"No."}}
                        """)));
        assertThrows(IllegalArgumentException.class, () -> ProfessionFeedbackDocument.Rule.parse(
                id("test:source"), id("test:beekeeper"), "bad", object("""
                        {"schema":"townstead:profession_feedback_rule/v1","trigger":"sometimes",
                         "dialogue":{"translate":"dialogue.test.bad"}}
                        """)));
    }

    private static ProfessionFeedbackDocument.Rule rule(String name, String translation) {
        return ProfessionFeedbackDocument.Rule.parse(
                id("test:profession/beekeeper/feedback/" + name), id("test:beekeeper"), name,
                object("{\"schema\":\"townstead:profession_feedback_rule/v1\","
                        + "\"dialogue\":{\"translate\":\"" + translation + "\"}}"));
    }

    private static JsonObject object(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    private static ResourceLocation id(String value) {
        //? if >=1.21 {
        return ResourceLocation.parse(value);
        //?} else {
        /*return new ResourceLocation(value);
        *///?}
    }
}
