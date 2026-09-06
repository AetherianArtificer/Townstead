package com.aetherianartificer.townstead.dialogue.contextual;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DialogueSelectorTest {
    @Test
    void largePoolExhaustsBeforeRepeatingEvenWithShortHistory() {
        var lines = new java.util.ArrayList<DialoguePalette.Line>();
        for (int i=0;i<20;i++) lines.add(new DialoguePalette.Line("line"+i,"dialogue.test."+i,1,Set.of(),Set.of(),Set.of()));
        DialoguePalette palette = new DialoguePalette(ResourceLocation.tryParse("test:large"),"test:large",0,2,lines);
        DialogueSelector selector = new DialogueSelector(); UUID speaker=UUID.randomUUID();
        DialogueRequest request=new DialogueRequest("test:large",Set.of(),Set.of(),Set.of());
        Set<String> chosen=new HashSet<>();
        for(int i=0;i<20;i++) assertTrue(chosen.add(selector.select(speaker,request,Set.of(palette),i,new Random(i)).orElseThrow().key()));
        assertTrue(selector.select(speaker,request,Set.of(palette),21,new Random()).isPresent());
    }
    @Test
    void exhaustsEligiblePoolBeforeRepeatingAndHonorsCooldown() {
        DialoguePalette palette = DialoguePalette.parse(ResourceLocation.tryParse("test:smalltalk"),
                JsonParser.parseString("""
                    {"intent":"social:smalltalk","min_interval_ticks":20,"history_size":8,"lines":[
                      {"translation":"dialogue.test.one"},
                      {"translation":"dialogue.test.two"},
                      {"translation":"dialogue.test.three"}
                    ]}
                    """).getAsJsonObject());
        DialogueSelector selector = new DialogueSelector();
        UUID speaker = UUID.randomUUID();
        DialogueRequest request = new DialogueRequest("social:smalltalk", Set.of(), Set.of(), Set.of());
        Set<String> firstCycle = new HashSet<>();
        for (long now : new long[]{0, 20, 40}) {
            firstCycle.add(selector.select(speaker, request, Set.of(palette), now, new Random(4)).orElseThrow().translation());
        }
        assertEquals(3, firstCycle.size());
        assertTrue(selector.select(speaker, request, Set.of(palette), 59, new Random()).isEmpty());
        assertTrue(selector.select(speaker, request, Set.of(palette), 60, new Random()).isPresent());
    }

    @Test
    void filtersContextPersonalityAndRelationshipWithoutMakingThemMandatory() {
        DialoguePalette palette = DialoguePalette.parse(ResourceLocation.tryParse("test:voice"),
                JsonParser.parseString("""
                    {"intent":"greet","min_interval_ticks":0,"lines":[
                      {"translation":"dialogue.test.rain","context":["weather:rain"]},
                      {"translation":"dialogue.test.friend","relationship":["friend"]},
                      {"translation":"dialogue.test.peppy","personality":["mca:peppy"]},
                      {"translation":"dialogue.test.general"}
                    ]}
                    """).getAsJsonObject());
        DialogueRequest request = new DialogueRequest("greet", Set.of("weather:clear"),
                Set.of("mca:introverted"), Set.of("stranger"));
        Optional<DialoguePalette.Line> selected = new DialogueSelector().select(UUID.randomUUID(), request,
                Set.of(palette), 0, new Random(1));
        assertEquals("dialogue.test.general", selected.orElseThrow().translation());
    }

    @Test
    void rejectsRawEnglishLines() {
        assertThrows(IllegalArgumentException.class, () -> DialoguePalette.parse(ResourceLocation.tryParse("test:bad"),
                JsonParser.parseString("""
                    {"intent":"greet","lines":[{"translation":"What a lovely day!"}]}
                    """).getAsJsonObject()));
    }
}
