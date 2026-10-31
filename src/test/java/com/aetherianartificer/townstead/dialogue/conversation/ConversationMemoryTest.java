package com.aetherianartificer.townstead.dialogue.conversation;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ConversationMemoryTest {
    @Test void supportMemoryKeepsTheCorrectPerspectiveThroughTheWorldSaveEnvelope() {
        ConversationSavedData data=new ConversationSavedData();
        data.memory().complete(a,"A",b,"B","test:help",
                new ConversationTopic.Outcome("test:help",2,1,1,1,"test:received_help","test:gave_help"),10);
        //? if >=1.21 {
        var encoded=data.save(new net.minecraft.nbt.CompoundTag(),null);
        ConversationMemory loaded=ConversationSavedData.load(encoded,null).memory();
        //?} else {
        /*var encoded=data.save(new net.minecraft.nbt.CompoundTag());
        ConversationMemory loaded=ConversationSavedData.load(encoded).memory();
        *///?}
        assertEquals("test:received_help",loaded.view(a,b,20).memory());
        assertEquals("test:gave_help",loaded.view(b,a,20).memory());
    }
    @Test void meetingDifferentNeighborsCannotBypassTheActorDailyBudget() {
        ConversationMemory memory=new ConversationMemory();
        for(int i=0;i<4;i++) assertTrue(memory.complete(a,"A",UUID.randomUUID(),"Other","test:topic",outcome(1,1),i));
        assertFalse(memory.complete(a,"A",b,"B","test:topic",outcome(4,4),5));
        assertEquals(0,memory.view(a,b,5).opinion());
    }
    @Test void durablePathStillConsumesRewardBudgetAndAdvancesOperationIdentity() {
        ConversationMemory memory=new ConversationMemory();
        var first=memory.completeDetailed(a,"A",b,"B","test:topic",outcome(2,2),10,false);
        var second=memory.completeDetailed(a,"A",b,"B","test:topic",outcome(2,2),20,false);
        var blocked=memory.completeDetailed(a,"A",b,"B","test:topic",outcome(2,2),30,false);
        assertTrue(first.rewarded()); assertTrue(second.rewarded()); assertFalse(blocked.rewarded());
        assertNotEquals(first.operationId(),second.operationId());
        assertEquals(0,memory.view(a,b,30).opinion(),"legacy opinion must remain frozen during the durable path");
    }
    private final UUID a=UUID.randomUUID(), b=UUID.randomUUID();
    private static ConversationTopic.Outcome outcome(int a,int b) { return new ConversationTopic.Outcome("test:reassured",a,b,1,1); }
    @Test void directionalOpinionsAndDailyBudgetSurviveSaveLoadAndReversingSpeaker() {
        ConversationMemory memory=new ConversationMemory();
        assertTrue(memory.complete(a,"A",b,"B","test:topic",outcome(3,-1),100));
        assertTrue(memory.complete(b,"B",a,"A","test:topic",outcome(2,1),1500));
        memory=ConversationMemory.load(memory.save());
        assertFalse(memory.complete(a,"A",b,"B","test:topic",outcome(4,4),3000));
        assertEquals(4,memory.view(a,b,3000).opinion()); assertEquals(1,memory.view(b,a,3000).opinion());
        assertEquals("test:reassured",memory.view(a,b,3000).memory());
        assertTrue(memory.complete(a,"A",b,"B","test:topic",outcome(1,1),24100));
        assertEquals(5,memory.view(a,b,24100).opinion());
    }
    @Test void friendshipRequiresMutualHistoryAndExpires() {
        ConversationMemory memory=new ConversationMemory();
        for (long now:new long[]{0,1500,24000,25500}) memory.complete(a,"A",b,"B","test:topic",outcome(3,3),now);
        assertEquals(Map.of(b,"B"),memory.friends(a,26000));
        assertTrue(memory.view(a,b,26000).relationships().contains("friend"));
        assertTrue(memory.friends(a,800000).isEmpty());
        assertEquals(Set.of("stranger"),memory.view(a,b,800000).relationships());
    }
    @Test void memoryAndOpinionRemainBoundedAndInvalidPairNeverAwards() {
        ConversationMemory memory=new ConversationMemory();
        assertFalse(memory.complete(a,"A",a,"A","test:topic",outcome(4,4),0));
        for(int i=0;i<30;i++) memory.complete(a,"A",b,"B","test:"+i,outcome(4,-4),i*24000L);
        assertEquals(30,memory.view(a,b,29*24000L).opinion()); assertEquals(-30,memory.view(b,a,29*24000L).opinion());
        assertEquals(8,memory.view(a,b,29*24000L).topics().size());
        assertTrue(memory.friends(a,29*24000L).isEmpty());
        assertEquals(-30,ConversationMemory.load(memory.save()).view(b,a,29*24000L).opinion());
    }
}
