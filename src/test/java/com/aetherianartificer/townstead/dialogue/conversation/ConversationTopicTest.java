package com.aetherianartificer.townstead.dialogue.conversation;

import com.aetherianartificer.townstead.dialogue.contextual.DialogueRequest;
import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ConversationTopicTest {
    static JsonObject graph() {
        return JsonParser.parseString("""
            {"schema":"townstead:conversation/v1","turns":{
              "open":{"speaker":"initiator","move":"townstead:say","lines":["dialogue.test.ask"],"duration":"2s","replies":["yes","no"]},
              "yes":{"speaker":"responder","move":"townstead:say","lines":["dialogue.test.yes"],"outcome":{"memory":"test:pleasant","initiator_opinion":2}},
              "no":{"speaker":"responder","move":"townstead:agree","outcome":{"memory":"test:awkward","responder_opinion":-1}}
            }}
            """).getAsJsonObject();
    }
    static ConversationTopic parse(JsonObject json) { return ConversationTopic.parse(ResourceLocation.tryParse("test:x"), json); }

    @Test void turnsNameAMoveAndMayCarryAuthoredLines() {
        ConversationTopic topic = parse(graph());
        assertEquals("open", topic.start());
        assertEquals(ResourceLocation.tryParse("townstead:say"), topic.turns().get("open").move());
        assertEquals(40, topic.turns().get("open").duration());
        assertTrue(topic.turns().get("no").lines().isEmpty(), "a composed turn has no authored lines");
        assertEquals(-1, topic.turns().get("no").duration(), "an untimed turn is timed by its length");
        assertTrue(topic.turns().get("yes").terminal());
    }
    @Test void aTurnWithoutAMoveIsRejected() {
        JsonObject bad = graph(); bad.getAsJsonObject("turns").getAsJsonObject("yes").remove("move");
        assertTrue(assertThrows(IllegalArgumentException.class, () -> parse(bad)).getMessage().contains("move"));
    }
    @Test void malformedGraphsFailWithLocatedDiagnostics() {
        JsonObject graph = graph(); graph.getAsJsonObject("turns").getAsJsonObject("open").add("replies", JsonParser.parseString("[\"absent\"]"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> parse(graph)).getMessage().contains("missing turn"));
        JsonObject cycle = graph(); cycle.getAsJsonObject("turns").getAsJsonObject("open").add("replies", JsonParser.parseString("[\"open\"]"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> parse(cycle)).getMessage().contains("cycle"));
        JsonObject typo = graph(); typo.getAsJsonObject("turns").getAsJsonObject("yes").addProperty("duraton", 40);
        assertTrue(assertThrows(IllegalArgumentException.class, () -> parse(typo)).getMessage().contains("turns.yes"));
        JsonObject early = graph(); early.getAsJsonObject("turns").getAsJsonObject("open")
                .add("outcome", JsonParser.parseString("{\"memory\":\"test:x\"}"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> parse(early)).getMessage().contains("without replies"));
    }
    @Test void topicLevelFieldsParse() {
        JsonObject graph = graph();
        graph.add("subjects", JsonParser.parseString("[\"townstead:event\"]"));
        graph.add("associated", JsonParser.parseString("[\"townstead:mob\"]"));
        graph.add("expansions", JsonParser.parseString("{\"moves\":[\"townstead:elaborate\"],\"max\":2}"));
        graph.add("requires", JsonParser.parseString("[\"realm.member\"]"));
        graph.addProperty("register", "social");
        ConversationTopic topic = parse(graph);
        assertEquals(List.of("townstead:event"), topic.subjects());
        assertEquals(List.of("townstead:mob"), topic.associated());
        assertEquals(2, topic.expansionMax());
        assertEquals("social", topic.register());
        assertEquals(List.of("realm.member"), topic.requires());
    }
    @Test void softPersonalityPreferenceDoesNotMakeLinesExclusive() {
        JsonObject graph = graph(); graph.add("personality_weights", JsonParser.parseString("{\"friendly\":3,\"default\":1}"));
        ConversationTopic topic = parse(graph);
        var friendly = new DialogueRequest("x", Set.of(), Set.of("mca:friendly"), Set.of());
        var other = new DialogueRequest("x", Set.of(), Set.of("custom:quiet"), Set.of());
        assertEquals(3, topic.gate().weight(friendly, c -> true)); assertEquals(1, topic.gate().weight(other, c -> true));
    }
    @Test void relationshipWeightsInfluenceResponsesButHardGatesStillApply() {
        JsonObject graph = graph(); graph.add("relationship_weights", JsonParser.parseString("{\"friend\":2,\"strained\":0.5,\"default\":1}"));
        graph.addProperty("context", "weather:rain");
        var gate = parse(graph).gate();
        var friend = new DialogueRequest("x", Set.of("weather:rain"), Set.of(), Set.of("friend"));
        var strained = new DialogueRequest("x", Set.of("weather:rain"), Set.of(), Set.of("strained"));
        var dry = new DialogueRequest("x", Set.of(), Set.of(), Set.of("friend"));
        assertEquals(2, gate.weight(friend, c -> true)); assertEquals(0.5, gate.weight(strained, c -> true));
        assertEquals(0, gate.weight(dry, c -> true));
    }
    @Test void aMonologueCannotGrantConversationRewards() {
        JsonObject graph = graph();
        graph.getAsJsonObject("turns").entrySet().forEach(e -> e.getValue().getAsJsonObject().addProperty("speaker", "initiator"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> parse(graph)).getMessage().contains("both participants"));
    }
    @Test void conversationEvaluationUsesOrdinaryValuesAndRejectsUnavailableScores() {
        com.aetherianartificer.townstead.pheno.value.ValueTypes.register(new com.aetherianartificer.townstead.pheno.value.types.ArithmeticValueType());
        JsonObject graph = graph(); graph.add("evaluation", JsonParser.parseString("{\"type\":\"pheno:arithmetic\",\"values\":[2,3]}"));
        var gate = parse(graph).gate();
        var request = new DialogueRequest("x", Set.of(), Set.of(), Set.of());
        assertEquals(5, gate.weight(request, c -> true, v -> v.get(null)));
        assertEquals(0, gate.weight(request, c -> true, v -> Double.NaN));
        assertEquals(0, gate.weight(request, c -> true, v -> -2));
        assertEquals(100, gate.weight(request, c -> true, v -> 10000));
    }
}
