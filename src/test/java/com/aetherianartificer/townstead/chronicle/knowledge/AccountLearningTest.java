package com.aetherianartificer.townstead.chronicle.knowledge;

import com.aetherianartificer.townstead.chronicle.model.*;
import com.aetherianartificer.townstead.chronicle.template.ChronicleEventTemplate;
import com.aetherianartificer.townstead.chronicle.world.ChronicleWorld;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AccountLearningTest {
    @Test void privateExperienceCreatesAnAccountAndEffectsBeforeNotificationWithoutEnteringGossip() {
        List<String> calls=new ArrayList<>();
        ChronicleWorld world=(ChronicleWorld)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{ChronicleWorld.class},(proxy,method,args)->{
            calls.add(method.getName());
            if(method.getName().equals("assignAccountId")) return 12L;
            return null;
        });
        UUID knower=UUID.randomUUID(),other=UUID.randomUUID();
        var template=ChronicleEventTemplate.parse(ResourceLocation.tryParse("test:private"),JsonParser.parseString("""
            {"roles":[{"id":"friend"},{"id":"companion"}],"reach":"none",
             "impact":{"companion":{"mood":2,"sentiment":{"toward":"friend","delta":3},"memory":{"strength":2,"valence":0.8}}}}
            """).getAsJsonObject(),Map.of());
        ChronicleEvent event=new ChronicleEvent(1,template.id(),5,120000,ResourceLocation.tryParse("minecraft:overworld"),0,-1,
                "social",1,ChronicleEvent.REACH_NONE,-1,-1,false,
                List.of(new Participation("friend",ChronicleRef.villager(other,"B"))),Map.of());
        Account account=AccountLedger.learn(world,template,event,knower,true,"companion",SpreadChannel.WITNESS,-1,1,DistortionOverlay.NONE,5);
        assertEquals(12,account.accountId()); assertEquals(knower,account.knower());
        assertEquals(List.of("assignAccountId","appendAccount","addMoodImpact","adjustSentiment","addEpisodicMemory","onLearned"),calls);
        calls.clear();
        ChronicleEvent publicEvent=new ChronicleEvent(2,template.id(),5,120000,event.dimension(),0,-1,"social",1,
                ChronicleEvent.REACH_VILLAGE,-1,-1,false,event.participations(),Map.of());
        AccountLedger.learn(world,template,publicEvent,knower,false,null,SpreadChannel.WITNESS,-1,1,DistortionOverlay.NONE,5);
        assertEquals(List.of("assignAccountId","noteKnownStory","appendAccount","onLearned"),calls);
        calls.clear();
        AccountLedger.learn(world,template,event,knower,false,null,SpreadChannel.WITNESS,-1,1,DistortionOverlay.NONE,5,false);
        assertEquals(List.of("assignAccountId","appendAccount"),calls);
    }
}
