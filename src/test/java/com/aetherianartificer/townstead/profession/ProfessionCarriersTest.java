package com.aetherianartificer.townstead.profession;

import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.aetherianartificer.townstead.profession.def.JobSiteProvider;
import com.aetherianartificer.townstead.profession.def.ProfessionDef;
import com.aetherianartificer.townstead.profession.def.ProfessionDefs;
import com.aetherianartificer.townstead.profession.def.ProgressionTrack;
import com.aetherianartificer.townstead.profession.def.RetrainingPolicy;
import com.aetherianartificer.townstead.profession.def.UnlockModel;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProfessionCarriersTest {

    private static final ResourceLocation COOK = ResourceLocation.tryParse("test:cook");
    private static final ResourceLocation FARMER = ResourceLocation.tryParse("test:farmer");
    private static final ResourceLocation FOREIGN_CHEF = ResourceLocation.tryParse("other:chef");
    private static final ResourceLocation FOREIGN_COOK = ResourceLocation.tryParse("other:cook");
    private static final ResourceLocation FOREIGN_FARMER = ResourceLocation.tryParse("other:farmer");

    @AfterEach
    void reset() {
        ProfessionDefs.replaceAll(Map.of());
    }

    @Test
    void aliasesOfSeatedCareersAreCarriers() {
        ProfessionDef cook = def(COOK, List.of(new JobSiteProvider.Building(
                List.of("compat/farmersdelight/kitchen_l"), List.of(1, 1, 2))));
        ProfessionDef farmer = def(FARMER, List.of(new JobSiteProvider.JobBlock(
                Set.of(ResourceLocation.tryParse("minecraft:composter")))));
        ProfessionDefs.replaceAll(Map.of(COOK, cook, FARMER, farmer), Map.of(
                FOREIGN_COOK, new ProfessionDefs.Resolution(COOK, null),
                FOREIGN_CHEF, new ProfessionDefs.Resolution(COOK, "chef"),
                FOREIGN_FARMER, new ProfessionDefs.Resolution(FARMER, null)));

        assertEquals(cook, ProfessionCarriers.carriedCareer(FOREIGN_COOK),
                "a root alias of a seated career is hired by its seats");
        assertEquals(cook, ProfessionCarriers.carriedCareer(FOREIGN_CHEF),
                "a Path alias of a seated career is hired by its seats");
        assertNull(ProfessionCarriers.carriedCareer(COOK),
                "the career itself is not a carrier of itself");
        assertNull(ProfessionCarriers.carriedCareer(FOREIGN_FARMER),
                "aliases of job-block careers stay with vanilla hiring");
        assertNull(ProfessionCarriers.carriedCareer(ResourceLocation.tryParse("other:unknown")));
        assertNull(ProfessionCarriers.carriedCareer(null));
    }

    private static ProfessionDef def(ResourceLocation id, List<JobSiteProvider> sites) {
        return new ProfessionDef(id, null, null,
                new ProgressionTrack(List.of(0), 0, 0), UnlockModel.EXPERIENTIAL, 1,
                RetrainingPolicy.FREE, List.of(), false, Conditions.ALWAYS, List.of(), sites);
    }
}
