package com.aetherianartificer.townstead.social;

import com.aetherianartificer.townstead.chronicle.model.VillagerMemory;
import com.aetherianartificer.townstead.chronicle.store.ChronicleSavedData;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SocialMemoryDefinitionTest {
    @Test void socialInclinationsAreStableDistinctAndStrictlyAuthored() {
        var json=JsonParser.parseString("""
                {"schema":"townstead:social_inclination/v1","display":{"text":"Kindness"},"base":50,"variation":20,
                 "personality_modifiers":{"friendly":15,"crabby":-15}}
                """).getAsJsonObject();
        var definition=SocialInclinationDefinition.parse(ResourceLocation.tryParse("test:kindness"),json,Map.of());
        UUID first=UUID.fromString("00000000-0000-0000-0000-000000000001"),second=UUID.fromString("00000000-0000-0000-0000-000000000002");
        assertEquals(SocialInclinations.score(first,"friendly",definition),SocialInclinations.score(first,"friendly",definition));
        assertNotEquals(SocialInclinations.score(first,"friendly",definition),SocialInclinations.score(second,"friendly",definition));
        assertTrue(SocialInclinations.score(first,"friendly",definition)>SocialInclinations.score(first,"crabby",definition));
        var invalid=json.deepCopy();invalid.addProperty("variation",101);
        assertThrows(IllegalArgumentException.class,()->SocialInclinationDefinition.parse(ResourceLocation.tryParse("test:bad"),invalid,Map.of()));
    }
    @Test void authoredForgettingAndPresentationParseStrictly() {
        var json=JsonParser.parseString("""
                {"schema":"townstead:social_memory/v1","display":{"text":"Kept a promise"},
                 "experience":{"default_strength":4,"default_valence":0.8},
                 "forgetting":{"half_life_days":100,"forget_below":0.1,"retention_priority":70}}
                """).getAsJsonObject();
        var definition=SocialMemoryDefinition.parse(ResourceLocation.tryParse("test:kept_promise"),json,Map.of());
        assertEquals("Kept a promise",definition.displayLiteral());
        assertEquals(100,definition.halfLifeDays()); assertEquals(70,definition.retentionPriority());
        var invalid=json.deepCopy(); invalid.addProperty("typo",1);
        assertTrue(assertThrows(IllegalArgumentException.class,()->SocialMemoryDefinition.parse(
                ResourceLocation.tryParse("test:bad"),invalid,Map.of())).getMessage().contains("typo"));
    }

    @Test void episodeRetainsSourcePolicyAndIdentityWhileForgetting() {
        var definition=new SocialMemoryDefinition(ResourceLocation.tryParse("test:promise"),"","Promise",
                4,0.8f,10,0.1f,70);
        var other=UUID.randomUUID();
        var memory=new VillagerMemory("test:promise",other,5,4,0.8f,Map.of("place","forge"),
                "event:9:memory","test:event",definition);
        for(int i=0;i<10;i++) memory.decayDaily(0.97f);
        assertEquals(2,memory.strength(),0.001);
        assertTrue(memory.episodic()); assertTrue(memory.matchesOperation("event:9:memory"));
        assertEquals("test:event",memory.source()); assertEquals(70,memory.retentionPriority());
        assertEquals(other,memory.otherParty()); assertEquals("forge",memory.params().get("place"));
    }

    @Test void episodicWritesAreRepeatSafe() {
        var id=ResourceLocation.tryParse("test:promise");
        SocialMemories.replaceAll(Map.of(id,new SocialMemoryDefinition(id,"","Promise",4,0.8f,10,0.1f,70)));
        var data=new ChronicleSavedData();UUID knower=UUID.randomUUID(),other=UUID.randomUUID();
        assertTrue(data.addEpisodicMemory(knower,"event:9:memory","test:promise",other,5,"test:event",Map.of()));
        assertFalse(data.addEpisodicMemory(knower,"event:9:memory","test:promise",other,5,"test:event",Map.of()));
        assertEquals(1,data.memoriesFor(knower).size());
    }
}
