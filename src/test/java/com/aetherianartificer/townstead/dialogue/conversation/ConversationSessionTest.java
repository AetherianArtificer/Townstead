package com.aetherianartificer.townstead.dialogue.conversation;

import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ConversationSessionTest {
    static JsonObject graph() {
        return JsonParser.parseString("""
            {"schema":"townstead:conversation/v1","turns":{
              "open":{"speaker":"initiator","lines":["dialogue.test.ask"],"duration":"2s","replies":["yes","no"]},
              "yes":{"speaker":"responder","lines":["dialogue.test.yes"],"outcome":{"memory":"test:pleasant","initiator_opinion":2}},
              "no":{"speaker":"responder","lines":["dialogue.test.no"],"outcome":{"memory":"test:awkward","responder_opinion":-1}}
            }}
            """).getAsJsonObject();
    }
    static ConversationTopic topic() { return ConversationTopic.parse(ResourceLocation.tryParse("test:topic"), graph()); }
    @Test void waitsForActualSpeechAndConnectedReplyBeforeCommitting() {
        UUID a=UUID.randomUUID(), b=UUID.randomUUID();
        ConversationSession s=new ConversationSession(topic(),a,b,0);
        s.advance(400,true, turn -> 1,new Random(1));
        assertEquals("open",s.turn().id()); assertNull(s.outcome());
        s.spoken(400); s.advance(439,true, turn -> 1,new Random(1));
        assertEquals(ConversationSession.Status.WAITING,s.status());
        s.advance(440,true, turn -> turn.id().equals("yes") ? 1 : 0,new Random(1));
        assertEquals(b,s.speaker()); assertEquals(a,s.listener()); assertEquals("yes",s.turn().id());
        assertNull(s.outcome()); s.spoken(440);
        s.advance(510,true, turn -> 1,new Random(1));
        assertEquals("test:pleasant",s.outcome().memory());
    }
    @Test void leavingOrOpeningPlayerDialogueCancelsEvenTheLastLineWithoutReward() {
        ConversationSession s=new ConversationSession(topic(),UUID.randomUUID(),UUID.randomUUID(),0);
        s.spoken(0); s.advance(40,true,t -> t.id().equals("no") ? 1 : 0,new Random()); s.spoken(40);
        s.advance(110,false,t -> 1,new Random());
        assertEquals(ConversationSession.Status.CANCELLED,s.status()); assertNull(s.outcome());
        s.advance(200,true,t -> 1,new Random()); assertNull(s.outcome());
    }
    @Test void unavailableResponsesAndSpeechStarvationTerminateWithoutFabricatedOutcome() {
        ConversationSession s=new ConversationSession(topic(),UUID.randomUUID(),UUID.randomUUID(),0);
        s.spoken(0); s.advance(40,true,t -> 0,new Random());
        assertEquals("no_eligible_reply",s.reason()); assertNull(s.outcome());
        s=new ConversationSession(topic(),UUID.randomUUID(),UUID.randomUUID(),0);
        s.advance(1800,true,t -> 1,new Random()); assertEquals("timeout",s.reason());
    }
    @Test void malformedGraphsFailWithLocatedDiagnostics() {
        JsonObject graph=graph(); graph.getAsJsonObject("turns").getAsJsonObject("open").add("replies",JsonParser.parseString("[\"absent\"]"));
        assertTrue(assertThrows(IllegalArgumentException.class,()->ConversationTopic.parse(ResourceLocation.tryParse("test:x"),graph)).getMessage().contains("missing turn"));
        JsonObject cycle=graph(); cycle.getAsJsonObject("turns").getAsJsonObject("open").add("replies",JsonParser.parseString("[\"open\"]"));
        assertTrue(assertThrows(IllegalArgumentException.class,()->ConversationTopic.parse(ResourceLocation.tryParse("test:x"),cycle)).getMessage().contains("cycle"));
        JsonObject bad=graph(); bad.getAsJsonObject("turns").getAsJsonObject("yes").addProperty("duraton",40);
        assertTrue(assertThrows(IllegalArgumentException.class,()->ConversationTopic.parse(ResourceLocation.tryParse("test:x"),bad)).getMessage().contains("turns.yes"));
    }
    @Test void softPersonalityPreferenceDoesNotMakeLinesExclusive() {
        JsonObject graph=graph(); graph.add("personality_weights",JsonParser.parseString("{\"friendly\":3,\"default\":1}"));
        ConversationTopic topic=ConversationTopic.parse(ResourceLocation.tryParse("test:x"),graph);
        var friendly=new com.aetherianartificer.townstead.dialogue.contextual.DialogueRequest("x",Set.of(),Set.of("mca:friendly"),Set.of());
        var other=new com.aetherianartificer.townstead.dialogue.contextual.DialogueRequest("x",Set.of(),Set.of("custom:quiet"),Set.of());
        assertEquals(3,topic.gate().weight(friendly,c -> true)); assertEquals(1,topic.gate().weight(other,c -> true));
    }
    @Test void relationshipWeightsInfluenceResponsesButHardGatesStillApply() {
        JsonObject graph=graph(); graph.add("relationship_weights",JsonParser.parseString("{\"friend\":2,\"strained\":0.5,\"default\":1}"));
        graph.addProperty("context", "weather:rain");
        var gate=ConversationTopic.parse(ResourceLocation.tryParse("test:x"),graph).gate();
        var friend=new com.aetherianartificer.townstead.dialogue.contextual.DialogueRequest("x",Set.of("weather:rain"),Set.of(),Set.of("friend"));
        var strained=new com.aetherianartificer.townstead.dialogue.contextual.DialogueRequest("x",Set.of("weather:rain"),Set.of(),Set.of("strained"));
        var dry=new com.aetherianartificer.townstead.dialogue.contextual.DialogueRequest("x",Set.of(),Set.of(),Set.of("friend"));
        assertEquals(2,gate.weight(friend,c -> true)); assertEquals(0.5,gate.weight(strained,c -> true));
        assertEquals(0,gate.weight(dry,c -> true));
    }
    @Test void aMonologueCannotGrantConversationRewards() {
        JsonObject graph=graph();
        graph.getAsJsonObject("turns").entrySet().forEach(e -> e.getValue().getAsJsonObject().addProperty("speaker","initiator"));
        assertTrue(assertThrows(IllegalArgumentException.class,()->ConversationTopic.parse(ResourceLocation.tryParse("test:x"),graph))
                .getMessage().contains("both participants"));
    }
    @Test void conversationEvaluationUsesOrdinaryValuesAndRejectsUnavailableScores() {
        com.aetherianartificer.townstead.pheno.value.ValueTypes.register(new com.aetherianartificer.townstead.pheno.value.types.ArithmeticValueType());
        JsonObject graph=graph(); graph.add("evaluation",JsonParser.parseString("{\"type\":\"pheno:arithmetic\",\"values\":[2,3]}"));
        var gate=ConversationTopic.parse(ResourceLocation.tryParse("test:x"),graph).gate();
        var request=new com.aetherianartificer.townstead.dialogue.contextual.DialogueRequest("x",Set.of(),Set.of(),Set.of());
        assertEquals(5,gate.weight(request,c -> true,v -> v.get(null)));
        assertEquals(0,gate.weight(request,c -> true,v -> Double.NaN));
        assertEquals(0,gate.weight(request,c -> true,v -> -2));
        assertEquals(100,gate.weight(request,c -> true,v -> 10000));
    }
}
