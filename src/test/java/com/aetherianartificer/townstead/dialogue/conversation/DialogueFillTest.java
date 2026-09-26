package com.aetherianartificer.townstead.dialogue.conversation;

import com.aetherianartificer.townstead.dialogue.conversation.generative.DialogueText;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DialogueFillTest {
    private static final Map<String, DialogueText.Value> VALUES = Map.of(
            "who", new DialogueText.Value("Ilse", null, Map.of("gender", "f")),
            "other2", new DialogueText.Value("Tomas", null, Map.of("gender", "m")),
            "biome", new DialogueText.Value("the plains", null, Map.of("number", "plural")));

    @Test void fillsNamedPlaceholders() {
        assertEquals("Ilse and Tomas had words.", DialogueText.fill("{who} and {other2} had words.", VALUES, "en_us"));
    }

    @Test void selectsFormsByGrammaticalFacts() {
        String template = "{who} {who.gender:m=est marié|f=est mariée|*=est marié·e} avec {other2}.";
        assertEquals("Ilse est mariée avec Tomas.", DialogueText.fill(template, VALUES, "fr_fr"));
        assertEquals("the plains are", DialogueText.fill("{biome} {biome.number:plural=are|*=is}", VALUES, "en_us"));
    }

    @Test void usesTheDefaultFormForAnUnknownValue() {
        assertEquals("marié·e", DialogueText.fill("{nobody.gender:m=marié|f=mariée|*=marié·e}", VALUES, "fr_fr"));
    }
}
