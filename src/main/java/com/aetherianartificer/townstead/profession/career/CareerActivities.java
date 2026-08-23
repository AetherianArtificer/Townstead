package com.aetherianartificer.townstead.profession.career;

import com.aetherianartificer.townstead.profession.def.ProfessionDef;
import com.aetherianartificer.townstead.profession.def.WorkTaskDef;
import com.aetherianartificer.townstead.profession.def.WorkTaskTypes;
import com.aetherianartificer.townstead.work.job.WorkJobDef;
import com.aetherianartificer.townstead.work.job.WorkJobs;
import net.minecraft.resources.ResourceLocation;

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
                counters.add(job.activityKey());
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

    private static boolean matches(WorkTaskDef task, WorkJobDef job) {
        WorkJobDef.BlockTarget target = job.target();
        if (target != null) {
            for (ResourceLocation block : target.blocks()) {
                if (task.allowsBlock(block)) return true;
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
}
