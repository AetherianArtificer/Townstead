package com.aetherianartificer.townstead.dialogue.conversation;

import com.google.gson.*;
import com.aetherianartificer.townstead.pheno.condition.ConditionTypes;
import com.aetherianartificer.townstead.pheno.condition.types.*;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ConversationContentTest {
    @Test void allBundledBranchesAreLocalizedAndPresentationReferencesResolve() throws Exception {
        ConditionTypes.register(new ProfessionConditionType());
        ConditionTypes.register(new NumericConditionType("pheno:hunger",ctx -> 50));
        ConditionTypes.register(new ValueConditionType());
        com.aetherianartificer.townstead.pheno.value.ValueTypes.register(new com.aetherianartificer.townstead.pheno.value.types.IfValueType());
        com.aetherianartificer.townstead.pheno.value.ValueTypes.register(new com.aetherianartificer.townstead.pheno.value.types.ChronicleValueType(
                com.aetherianartificer.townstead.pheno.value.types.ChronicleValueType.Kind.SENTIMENT));
        Path resources=Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("data/townstead_social")).toURI()).getParent().getParent();
        JsonObject lang=JsonParser.parseString(Files.readString(resources.resolve("data/townstead_social/lang/en_us.json"))).getAsJsonObject();
        JsonObject client=JsonParser.parseString(Files.readString(resources.resolve("assets/townstead_social/lang/en_us.json"))).getAsJsonObject();
        assertEquals(lang,client);
        int topics=0,lines=0,relationshipChanges=0,episodicOutcomes=0;
        try(var files=Files.list(resources.resolve("data/townstead_social/conversation"))) {
            for(Path file:files.filter(p -> p.toString().endsWith(".json")).toList()) {
                String name=file.getFileName().toString().replace(".json","");
                ConversationTopic topic=ConversationTopic.parse(ResourceLocation.tryParse("townstead_social:"+name),JsonParser.parseString(Files.readString(file)).getAsJsonObject());
                topics++;
                for(var turn:topic.turns().values()) {
                    for(String line:turn.lines()) { assertTrue(lang.has(line),line); assertFalse(lang.get(line).getAsString().isBlank()); lines++; }
                    for(var cue:List.of(turn.gesture(),turn.listener())) {
                        if(cue.performance()!=null) assertTrue(Files.exists(resources.resolve("data/"+cue.performance().getNamespace()+"/performance_mapping/"+cue.performance().getPath()+".json")),cue.performance().toString());
                    }
                    if(turn.outcome()!=null) {
                        for(String memory:List.of(turn.outcome().initiatorMemory(),turn.outcome().responderMemory())) {
                            ResourceLocation memoryId=ResourceLocation.tryParse(memory);
                            assertNotNull(memoryId,memory);
                            Path definition=resources.resolve("data/"+memoryId.getNamespace()+"/social_memory/"+memoryId.getPath()+".json");
                            assertTrue(Files.exists(definition),memory);
                            var parsed=com.aetherianartificer.townstead.social.SocialMemoryDefinition.parse(memoryId,
                                    JsonParser.parseString(Files.readString(definition)).getAsJsonObject(),Map.of());
                            assertTrue(lang.has(parsed.displayLangKey()),parsed.displayLangKey());
                            episodicOutcomes++;
                        }
                        for(var change:java.util.stream.Stream.concat(
                                turn.outcome().initiatorRelationship().stream(),turn.outcome().responderRelationship().stream()).toList()) {
                            ResourceLocation quality=ResourceLocation.tryParse(change.quality());
                            assertNotNull(quality,change.quality());
                            assertTrue(Files.exists(resources.resolve("data/"+quality.getNamespace()+"/relationship_quality/"+quality.getPath()+".json")),change.quality());
                            relationshipChanges++;
                        }
                    }
                }
            }
        }
        assertTrue(topics>=10); assertTrue(lines>=140); assertTrue(relationshipChanges>=30); assertTrue(episodicOutcomes>=20);
    }
    @Test void archiveTemplateRecordsParticipantsWithoutDuplicatingRewardsOrSpreadingPrivateTalk() throws Exception {
        var url=Objects.requireNonNull(getClass().getClassLoader().getResource("data/townstead_social/chronicle_event/conversation.json"));
        JsonObject json=JsonParser.parseString(Files.readString(Path.of(url.toURI()))).getAsJsonObject();
        var template=com.aetherianartificer.townstead.chronicle.template.ChronicleEventTemplate.parse(
                ResourceLocation.tryParse("townstead_social:conversation"),json,Map.of());
        assertEquals(0,template.reach()); assertFalse(template.keep());
        assertEquals(List.of("initiator","responder"),template.roles().stream().map(r -> r.id()).toList());
        assertTrue(template.impacts().isEmpty()); assertTrue(template.effects().isEmpty());
    }
}
