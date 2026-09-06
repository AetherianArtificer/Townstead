package com.aetherianartificer.townstead.root.gene;

import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.aetherianartificer.townstead.pheno.condition.PhenoSubject;
import com.aetherianartificer.townstead.pheno.condition.types.LifeStageConditionType;
import com.aetherianartificer.townstead.root.CanonicalStage;
import com.aetherianartificer.townstead.root.gene.types.LifeCycleGeneType;
import com.aetherianartificer.townstead.social.Bonds;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LifeStageTagTest {
    private static final ResourceLocation ADULT = id("townstead_lifecycle:adult");
    private static final ResourceLocation ALCOHOL = id("townstead_lifecycle:can_consume_alcohol");

    @Test
    void legacyAdultStagesReceiveCompatibilityTags() {
        LifeCycleGeneType.Instance parsed = parse("""
                {"stages":[{"id":"grown","presents_as":"adult","days":10}]}
                """);

        assertTrue(parsed.cycle().stageAt(0).tags().contains(ADULT));
        assertTrue(parsed.cycle().stageAt(0).tags().contains(ALCOHOL));
    }

    @Test
    void explicitTagsReplaceDefaultsEvenWhenEmpty() {
        LifeCycleGeneType.Instance parsed = parse("""
                {"stages":[
                  {"id":"abstaining_adult","presents_as":"adult","days":10,"tags":[]},
                  {"id":"independent_teen","presents_as":"teen","days":10,
                   "tags":["example:may_enter_lounge"]}
                ]}
                """);

        assertFalse(parsed.cycle().stageAt(0).tags().contains(ALCOHOL));
        assertTrue(parsed.cycle().stageAt(1).tags().contains(id("example:may_enter_lounge")));
    }

    @Test
    void phenoLifeStageTagReadsSubjectCapabilitiesRatherThanCanonicalAge() {
        var condition = new LifeStageConditionType().parse(JsonParser.parseString("""
                {"tag":"townstead_lifecycle:can_consume_alcohol"}
                """).getAsJsonObject());
        PhenoSubject independentlyCapableTeen = new PhenoSubject() {
            @Override public UUID uuid() { return new UUID(0L, 1L); }
            @Override public String displayName() { return "Test"; }
            @Override public String professionId() { return ""; }
            @Override public CanonicalStage lifeStage() { return CanonicalStage.TEEN; }
            @Override public Set<ResourceLocation> lifeStageTags() { return Set.of(ALCOHOL); }
            @Override public Bonds bonds() { return Bonds.EMPTY; }
        };

        assertTrue(condition.supportsSubject());
        assertTrue(condition.test(new ConditionContext(independentlyCapableTeen)));
    }

    private static LifeCycleGeneType.Instance parse(String json) {
        return (LifeCycleGeneType.Instance) new LifeCycleGeneType()
                .parse(JsonParser.parseString(json).getAsJsonObject());
    }

    private static ResourceLocation id(String raw) {
        return ResourceLocation.tryParse(raw);
    }
}
