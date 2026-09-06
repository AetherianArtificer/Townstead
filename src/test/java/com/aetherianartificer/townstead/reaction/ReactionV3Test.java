package com.aetherianartificer.townstead.reaction;

import com.aetherianartificer.townstead.pheno.action.ActionTypes;
import com.aetherianartificer.townstead.pheno.action.types.AndActionType;
import com.aetherianartificer.townstead.pheno.action.types.ExpressionActionType;
import com.aetherianartificer.townstead.pheno.action.types.NothingActionType;
import com.aetherianartificer.townstead.pheno.action.types.PerformanceActionType;
import com.aetherianartificer.townstead.pheno.action.types.SpawnParticlesActionType;
import com.aetherianartificer.townstead.pheno.action.types.SocialOutcomeActionType;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ReactionV3Test {
    private static final List<String> BUNDLED = List.of(
            "applaud", "dance", "drowsy", "farewell_friend", "greet_player", "hunger_notice",
            "outsider_alarm", "raid_alarm", "raid_quieted", "shelter_relief",
            "spouse_nearby", "threat_passed", "thirst_notice", "wave", "witness_harm", "work_idea",
            "zombie_alarm");

    @BeforeAll
    static void registerActions() {
        ActionTypes.register(new NothingActionType());
        ActionTypes.register(new AndActionType());
        ActionTypes.register(new PerformanceActionType());
        ActionTypes.register(new ExpressionActionType());
        ActionTypes.register(new SpawnParticlesActionType());
        ActionTypes.register(new SocialOutcomeActionType());
    }

    private static JsonObject obj(String json) {
        return JsonParser.parseString(json.replace('\'', '"')).getAsJsonObject();
    }

    @Test
    void topLevelActionCreatesImplicitOutcome() {
        Reaction reaction = Reaction.parse(ResourceLocation.tryParse("example:thought"),
                obj("{'schema':'townstead:reaction/v3','do':{'type':'pheno:nothing'}}"));

        assertTrue(reaction.phenoAction().isPresent());
        assertEquals(1, reaction.bindings().size());
        assertFalse(reaction.bindings().get(0).hasAnimation());
        assertFalse(reaction.bindings().get(0).animationRequired());
    }

    @Test
    void actionOnlyChoiceNeedsNoAnimationBackend() {
        Reaction reaction = Reaction.parse(ResourceLocation.tryParse("example:thought"),
                obj("{'schema':'townstead:reaction/v3','choices':[{'do':{'type':'pheno:nothing'}}]}"));

        ReactionBinding outcome = assertDoesNotThrow(() -> reaction.bindings().get(0));
        assertFalse(outcome.hasAnimation());
        assertTrue(outcome.hasAuxiliaryOutput());
    }

    @Test
    void legacyAnimationIsOptionalBesideV3ActionButRequiredAlone() {
        Reaction mixed = Reaction.parse(ResourceLocation.tryParse("example:mixed"),
                obj("{'schema':'townstead:reaction/v3','choices':[{'ref':'emotecraft:wave',"
                        + "'do':{'type':'pheno:nothing'}}]}"));
        Reaction animationOnly = Reaction.parse(ResourceLocation.tryParse("example:animation"),
                obj("{'schema':'townstead:reaction/v3','choices':[{'ref':'emotecraft:wave'}]}"));

        assertFalse(mixed.bindings().get(0).animationRequired());
        assertTrue(animationOnly.bindings().get(0).animationRequired());
    }

    @Test
    void v2StillRejectsAnOutcomeWithoutAnimation() {
        Reaction reaction = Reaction.parse(ResourceLocation.tryParse("example:old"),
                obj("{'schema':'townstead:reaction/v2','choices':[{'do':{'type':'pheno:nothing'}}]}"));
        assertTrue(reaction.bindings().isEmpty());
    }

    @Test
    void everyBundledReactionParsesWithAUsableV3Outcome() {
        for (String name : BUNDLED) {
            String path = "/data/townstead/townstead/reactions/" + name + ".json";
            try (var stream = ReactionV3Test.class.getResourceAsStream(path)) {
                assertNotNull(stream, path);
                JsonObject json = JsonParser.parseReader(
                        new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
                assertEquals("townstead:reaction/v3", json.get("schema").getAsString(), name);
                Reaction reaction = Reaction.parse(ResourceLocation.tryParse("townstead:" + name), json);
                assertFalse(reaction.bindings().isEmpty(), name);
                assertTrue(reaction.bindings().stream().allMatch(
                        binding -> binding.hasAnimation() || binding.hasAuxiliaryOutput()
                                || reaction.phenoAction().isPresent()), name);
            } catch (java.io.IOException e) {
                fail(e);
            }
        }
    }

    @Test
    void bundledPersonalityWeightsUseMcaPersonalityIds() {
        Set<String> known = Set.of("default", "friendly", "flirty", "playful", "gloomy", "sensitive",
                "greedy", "odd", "crabby", "extroverted", "introverted", "relaxed", "anxious",
                "peaceful", "upbeat");
        for (String name : BUNDLED) {
            String path = "/data/townstead/townstead/reactions/" + name + ".json";
            try (var stream = ReactionV3Test.class.getResourceAsStream(path)) {
                assertNotNull(stream, path);
                JsonObject json = JsonParser.parseReader(
                        new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
                if (!json.has("choices")) continue;
                for (var choiceElement : json.getAsJsonArray("choices")) {
                    JsonObject choice = choiceElement.getAsJsonObject();
                    if (!choice.has("personality_weights")) continue;
                    for (String personality : choice.getAsJsonObject("personality_weights").keySet()) {
                        assertTrue(known.contains(personality), name + ": " + personality);
                    }
                }
            } catch (java.io.IOException e) {
                fail(e);
            }
        }
    }
}
