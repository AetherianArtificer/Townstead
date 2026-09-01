package com.aetherianartificer.townstead.profession.career;

import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.aetherianartificer.townstead.profession.def.WorkTaskDef;
import com.aetherianartificer.townstead.work.job.WorkJobDef;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CareerActivitiesTest {
    @Test
    void tagDefinedJobBelongsToCareerUsingTheSameWorkstationTag() {
        ResourceLocation taskId = id("townstead_work:interact");
        ResourceLocation beehives = id("minecraft:beehives");
        WorkTaskDef task = new WorkTaskDef(taskId,
                new WorkTaskDef.TargetSet(Set.of(), List.of(beehives), false, Set.of()),
                WorkTaskDef.TargetSet.EMPTY, WorkTaskDef.TargetSet.EMPTY,
                WorkTaskDef.TargetSet.EMPTY, 10, WorkTaskDef.Scope.WORKSITE, Conditions.ALWAYS);
        WorkJobDef.BlockTarget target = new WorkJobDef.BlockTarget(
                List.of(), Set.of(), List.of(beehives), WorkJobDef.Placement.DEFAULT,
                null, List.of(), List.of());
        WorkJobDef job = new WorkJobDef(id("townstead_beekeeping:beehive_harvest"),
                taskId, WorkJobDef.BLOCK_INTERACTION, null, null, target);

        assertTrue(CareerActivities.matches(task, job));
    }

    @Test
    void interactionActivitiesRemainSeparateEvidenceCounters() {
        WorkJobDef.Interaction comb = new WorkJobDef.Interaction(null, null, null, null,
                Set.of(), 3, 4, id("test:honeycomb_harvested"));
        WorkJobDef.Interaction bottle = new WorkJobDef.Interaction(null, null, null, null,
                Set.of(), 1, 4, id("test:honey_bottled"));
        WorkJobDef.BlockTarget target = new WorkJobDef.BlockTarget(
                List.of(), Set.of(id("minecraft:beehive")), List.of(),
                WorkJobDef.Placement.DEFAULT, null, List.of(), List.of(comb, bottle));
        WorkJobDef job = new WorkJobDef(id("test:beehive_harvest"),
                id("townstead_work:interact"), WorkJobDef.BLOCK_INTERACTION,
                null, null, target);

        assertEquals(List.of("test:honeycomb_harvested", "test:honey_bottled"),
                job.activityKeys());
    }

    private static ResourceLocation id(String value) {
        return ResourceLocation.tryParse(value);
    }
}
