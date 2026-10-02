package com.aetherianartificer.townstead.dialogue.conversation.generative;

import com.aetherianartificer.townstead.dialogue.conversation.ConversationTopic;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class GenerativeContentTest {

    @Test void shippedDataLoadsWithoutDiagnostics() throws Exception {
        GenerativeDialogue.Data data = ShippedDialogue.data();
        assertEquals(List.of(), data.diagnostics());
        assertTrue(data.ready());
        assertNotNull(data.encounter(ConversationEncounter.Context.IDLE));
        assertNotNull(data.encounter(ConversationEncounter.Context.HANGOUT));
        assertTrue(data.partsByPool().values().stream().mapToInt(List::size).sum() > 2000, "the common voice is rich");
    }

    @Test void everyPartHasEnglishWithKnownPlaceholders() throws Exception {
        GenerativeDialogue.Data data = ShippedDialogue.data();
        Set<String> slots = new HashSet<>(Set.of("other", "self"));
        data.subjects().values().forEach(subject -> slots.addAll(subject.slots().keySet()));
        for (List<DialoguePart> pool : data.partsByPool().values()) {
            for (DialoguePart part : pool) {
                if (part.empty()) continue;
                assertNotNull(part.english(), part.key());
                assertFalse(part.english().isBlank(), part.key());
                assertFalse(part.english().contains("%"), "old placeholder in " + part.key());
                for (String arg : part.args()) assertTrue(slots.contains(arg), part.key() + " uses unknown slot " + arg);
            }
        }
    }

    @Test void everyTopicMoveAndEncounterMoveExists() throws Exception {
        GenerativeDialogue.Data data = ShippedDialogue.data();
        for (ConversationTopic topic : ShippedDialogue.topics().values()) {
            for (ConversationTopic.Turn turn : topic.turns().values()) {
                assertTrue(data.moves().containsKey(turn.move()), topic.id() + " uses " + turn.move());
                assertTrue(data.framesByMove().containsKey(turn.move()) || !turn.lines().isEmpty(),
                        topic.id() + "/" + turn.id() + " has neither frames nor lines");
            }
            for (ResourceLocation move : topic.expansionMoves()) assertTrue(data.moves().containsKey(move), move.toString());
            for (String subject : topic.subjects()) {
                ResourceLocation id = ResourceLocation.tryParse(subject);
                assertTrue(subject.startsWith("#") || data.subjects().containsKey(id), topic.id() + " names " + subject);
            }
        }
    }

    @Test void personalityLinesListBothMcaGenerations() throws Exception {
        GenerativeDialogue.Data data = ShippedDialogue.data();
        Map<String, String> successors = Map.of("crabby", "grumpy", "upbeat", "peppy", "playful", "witty",
                "introverted", "shy", "extroverted", "confident", "relaxed", "lazy");
        for (List<DialoguePart> pool : data.partsByPool().values()) {
            for (DialoguePart part : pool) {
                for (var e : successors.entrySet()) {
                    if (part.personalities().contains(e.getKey()))
                        assertTrue(part.personalities().contains(e.getValue()), part.key() + " lacks " + e.getValue());
                }
            }
        }
    }
}
