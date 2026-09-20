package com.aetherianartificer.townstead.politics.founding;

import com.aetherianartificer.townstead.culture.CulturalSpawnBias;
import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.aetherianartificer.townstead.root.SpawnBias;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FoundingPopulationWeightsTest {
    @Test
    void everyRootGetsTheOutsiderBaselineWhileAffinityAndAdjustmentsIncreaseRepresentation() {
        ResourceLocation culture = id("example:marshside");
        ResourceLocation traveller = id("example:marsh_traveller");
        FoundingProfileDefinition profile = new FoundingProfileDefinition(id("example:marsh_founders"),
                Component.literal("Marsh Founders"), culture, 1.0F, SpawnBias.EMPTY, Conditions.ALWAYS,
                new FoundingProfileDefinition.Population(FoundingProfileDefinition.CULTURAL_AFFINITY,
                        0.1F, Map.of(traveller, 2.0F)),
                new FoundingProfileDefinition.Government(id("townstead:village_council"),
                        "{village} Council", List.of()));
        CulturalSpawnBias affinity = new CulturalSpawnBias(Map.of(
                culture.toString(), 4.0F,
                "any", 1.0F));

        assertEquals(10.2F, FoundingPopulationWeights.culturalAffinity(profile, traveller, affinity), 0.0001F);
        assertEquals(5.1F, FoundingPopulationWeights.culturalAffinity(
                profile, id("future_pack:new_root"), affinity), 0.0001F);
        assertEquals(0.1F, FoundingPopulationWeights.culturalAffinity(
                profile, id("future_pack:unconnected_root"), CulturalSpawnBias.EMPTY), 0.0001F);
    }

    private static ResourceLocation id(String value) {
        ResourceLocation parsed = DataPackLang.parseId(value);
        if (parsed == null) throw new IllegalArgumentException(value);
        return parsed;
    }
}
