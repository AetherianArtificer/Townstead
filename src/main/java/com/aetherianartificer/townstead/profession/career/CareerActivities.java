package com.aetherianartificer.townstead.profession.career;

import com.aetherianartificer.townstead.profession.def.ProfessionDef;
import com.aetherianartificer.townstead.profession.def.WorkTaskDef;
import com.aetherianartificer.townstead.profession.def.WorkTaskTypes;
import com.aetherianartificer.townstead.work.job.WorkJobDef;
import com.aetherianartificer.townstead.work.job.WorkJobs;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Completed-work history derived from what a Career can actually do. */
public final class CareerActivities {
    private CareerActivities() {}

    public static List<String> counters(ProfessionDef profession) {
        Set<String> counters = new LinkedHashSet<>();
        for (WorkTaskDef task : profession.workTasks()) {
            boolean hasJobs = false;
            for (WorkJobDef job : WorkJobs.all()) {
                if (!job.task().equals(task.type()) || !matches(task, job)) continue;
                counters.addAll(job.activityKeys());
                hasJobs = true;
            }
            if (!hasJobs) counters.addAll(WorkTaskTypes.activities(task.type()));
        }
        return List.copyOf(counters);
    }

    public static boolean isJob(String counter) {
        ResourceLocation id = ResourceLocation.tryParse(counter);
        return id != null && WorkJobs.byId(id) != null;
    }

    static boolean matches(WorkTaskDef task, WorkJobDef job) {
        WorkJobDef.BlockTarget target = job.target();
        if (target != null) {
            if (task.anyWorkstation()) return true;
            for (ResourceLocation block : target.blocks()) {
                if (task.allowsBlock(block)) return true;
            }

            // Pack-authored Jobs normally use tags (for example both halves of the Beekeeper
            // contract name #minecraft:beehives). Comparing only the target's literal blocks
            // made the Job run and grant XP while silently omitting its Chronicle counter from
            // the Career record. Identical selectors are the common, allocation-free path.
            for (ResourceLocation tag : target.blockTags()) {
                if (task.workstations().tags().contains(tag)) return true;
            }

            // Also admit genuinely overlapping selectors: a Career may name a narrower tag or
            // literal block than the Job. Registry iteration happens only when neither selector
            // was an immediate match, and only while building the small Career graph payload.
            for (Block block : BuiltInRegistries.BLOCK) {
                ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(block);
                if (task.allowsBlock(blockId) && targetAllows(target, blockId, block)) return true;
            }
            return false;
        }
        WorkJobDef.EntitySource source = job.source();
        if (source != null) {
            for (ResourceLocation entity : source.results().keySet()) {
                if (task.allowsEntityId(entity)) return true;
            }
            return false;
        }
        return true;
    }

    private static boolean targetAllows(WorkJobDef.BlockTarget target,
                                        ResourceLocation blockId, Block block) {
        if (target.blocks().contains(blockId)) return true;
        for (ResourceLocation tag : target.blockTags()) {
            if (block.defaultBlockState().is(TagKey.create(Registries.BLOCK, tag))) return true;
        }
        return false;
    }
}
