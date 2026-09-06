package com.aetherianartificer.townstead.pheno.value;

import com.aetherianartificer.townstead.pheno.condition.*;
import com.aetherianartificer.townstead.pheno.condition.types.ValueConditionType;
import com.aetherianartificer.townstead.pheno.selector.SelectorContext;
import com.aetherianartificer.townstead.pheno.value.types.*;
import com.aetherianartificer.townstead.root.CanonicalStage;
import com.aetherianartificer.townstead.social.*;
import com.google.gson.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SocialKnowledgeValueTest {
    static final UUID SELF=UUID.randomUUID(), OTHER=UUID.randomUUID(), THIRD=UUID.randomUUID();
    @BeforeAll static void register() {
        for (var kind:ChronicleValueType.Kind.values()) ValueTypes.register(new ChronicleValueType(kind));
        ValueTypes.register(new ArithmeticValueType()); ValueTypes.register(new IfValueType());
        ValueTypes.register(new BondCountValueType()); ConditionTypes.register(new ValueConditionType());
        ValueTypes.register(new RelationshipValueType());
        ValueTypes.register(new SocialInclinationValueType());
    }
    static JsonObject json(String text) { return JsonParser.parseString(text.replace('\'', '"')).getAsJsonObject(); }
    static PhenoSubject subject(SocialKnowledge knowledge) {
        return new PhenoSubject() {
            public UUID uuid(){return SELF;} public String displayName(){return "A";}
            public String professionId(){return "";} public CanonicalStage lifeStage(){return CanonicalStage.ADULT;}
            public Bonds bonds(){return Bonds.of(List.of(Bond.ongoing("test:friend",OTHER,"B",3),Bond.ongoing("test:friend",THIRD,"C",4)));}
            public int counter(String key){return key.equals("test:work")?42:0;}
            public SocialKnowledge socialKnowledge(){return knowledge;}
        };
    }
    static SocialKnowledge knowledge() {
        return new SocialKnowledge.Snapshot(20,List.of(
                new SocialKnowledge.Memory("test:help",OTHER,18,2,3,0.6),
                new SocialKnowledge.Memory("test:argument",OTHER,19,1,1,-0.6),
                new SocialKnowledge.Memory("test:help",THIRD,10,5,9,0.8)),Map.of(OTHER,7D,THIRD,-3D));
    }
    static SelectorContext frame() {return SelectorContext.of(new ConditionContext(subject(knowledge()),OTHER));}
    static Value value(String text){return Objects.requireNonNull(Values.parse(json(text)));}
    @Test void queriesAreDirectionalAndSeparateBeliefFromTruthCounters() {
        assertEquals(7,value("{'type':'pheno:sentiment'}").get(frame()));
        assertEquals(2,value("{'type':'pheno:memory','key':'test:help','about':'other'}").get(frame()));
        assertEquals(4,value("{'type':'pheno:memory','about':'other','metric':'strength'}").get(frame()));
        assertEquals(0.3,value("{'type':'pheno:memory','about':'other','metric':'valence'}").get(frame()),0.0001);
        assertEquals(1,value("{'type':'pheno:memory','about':'other','metric':'age_days'}").get(frame()));
        assertEquals(1,value("{'type':'pheno:bond_count','kind':'test:friend','toward':'other'}").get(frame()));
        assertEquals(42,value("{'type':'pheno:chronicle_count','key':'test:work'}").get(frame()));
    }
    @Test void unavailableKnowledgeAndMissingCounterpartsCannotPassNumericComparisons() {
        var condition=new ValueConditionType().parse(json("{'value':{'type':'pheno:sentiment'},'comparison':'!=','compare_to':0}"));
        assertFalse(condition.test(new ConditionContext(subject(knowledge()))));
        assertFalse(condition.test(new ConditionContext(subject(null),OTHER)));
        assertTrue(condition.test(new ConditionContext(subject(knowledge()),OTHER)));
        assertTrue(Double.isNaN(value("{'type':'pheno:memory','key':'test:missing','metric':'age_days'}").get(frame())));
        assertEquals(0,value("{'type':'pheno:memory','key':'test:missing'}").get(frame()));
    }
    @Test void conditionValueRoundTripsPreserveCounterpartAndComposeInOfflineEvaluations() {
        var original=new ConditionContext(subject(knowledge()),OTHER);
        assertEquals(OTHER,ConditionContext.of(SelectorContext.of(original)).otherId());
        Value score=value("""
            {'type':'pheno:arithmetic','operation':'multiply','values':[2,
              {'type':'pheno:if','entity_condition':{'type':'pheno:value',
                'value':{'type':'pheno:sentiment'},'comparison':'>','compare_to':3},'then':4,'else':0.5}]}
            """);
        assertTrue(score.supportsSubject()); assertEquals(8,score.get(frame()));
        assertEquals(1,score.get(SelectorContext.of(new ConditionContext(subject(knowledge()),THIRD))));
    }
    @Test void invalidMetricsAndTargetsAreRejected() {
        assertNull(Values.parse(json("{'type':'pheno:memory','metric':'strenght'}")));
        assertNull(Values.parse(json("{'type':'pheno:sentiment','toward':'any'}")));
        assertNull(Values.parse(json("{'type':'pheno:bond_count','kind':'test:friend','toward':'typo'}")));
        assertNull(Values.parse(json("{'type':'pheno:chronicle_count'}")));
        assertNull(Values.parse(json("{'type':'pheno:relationship','quality':'bad id'}")));
    }

    @Test void relationshipQualityIsReusableInOfflinePheno() {
        SocialKnowledge layered = new SocialKnowledge() {
            public long today(){return 20;} public List<Memory> memories(){return List.of();}
            public double sentiment(UUID toward){return 0;}
            public double relationship(UUID toward,String quality){return toward.equals(OTHER)&&quality.equals("test:respect")?35:0;}
        };
        Value respect=value("{'type':'pheno:relationship','quality':'test:respect'}");
        assertEquals(35,respect.get(SelectorContext.of(new ConditionContext(subject(layered),OTHER))));
        assertTrue(Double.isNaN(respect.get(SelectorContext.of(new ConditionContext(subject(layered))))));
    }
    @Test void socialInclinationsAreReusableInOfflinePheno() {
        SocialKnowledge valued = new SocialKnowledge() {
            public long today(){return 20;} public List<Memory> memories(){return List.of();}
            public double sentiment(UUID toward){return 0;}
            public double socialInclination(String id){return id.equals("test:kindness")?78:Double.NaN;}
        };
        assertEquals(78,value("{'type':'pheno:social_inclination','inclination':'test:kindness'}")
                .get(SelectorContext.of(new ConditionContext(subject(valued),OTHER))));
        assertNull(Values.parse(json("{'type':'pheno:social_inclination','inclination':'bad id'}")));
    }
}
