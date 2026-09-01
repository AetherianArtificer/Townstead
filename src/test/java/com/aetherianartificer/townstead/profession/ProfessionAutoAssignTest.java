package com.aetherianartificer.townstead.profession;

import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.aetherianartificer.townstead.profession.def.JobSiteProvider;
import com.aetherianartificer.townstead.profession.def.ProfessionDef;
import com.aetherianartificer.townstead.profession.def.ProgressionTrack;
import com.aetherianartificer.townstead.profession.def.RetrainingPolicy;
import com.aetherianartificer.townstead.profession.def.UnlockModel;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfessionAutoAssignTest {

    @Test
    void practicedBuildingDefinitionsAreDiscoveredWithoutHardcodedTaskIds() {
        assertTrue(ProfessionAutoAssign.managesDefinition(def(
                List.of(new JobSiteProvider.Building(List.of("test:apiary"))), List.of())));
        assertFalse(ProfessionAutoAssign.managesDefinition(def(
                List.of(new JobSiteProvider.JobBlock(Set.of(
                        ResourceLocation.tryParse("minecraft:lectern")))), List.of())));
        assertFalse(ProfessionAutoAssign.managesDefinition(def(
                List.of(new JobSiteProvider.Building(List.of("test:guild"))),
                List.of("mentor"))), "gated careers must still use their acquisition route");
    }

    private static ProfessionDef def(List<JobSiteProvider> sites, List<String> routes) {
        return new ProfessionDef(ResourceLocation.tryParse("test:subject"), null, null,
                new ProgressionTrack(List.of(0), 0, 0), UnlockModel.EXPERIENTIAL, 1,
                RetrainingPolicy.FREE, List.of(), false, Conditions.ALWAYS, routes, sites);
    }
}
