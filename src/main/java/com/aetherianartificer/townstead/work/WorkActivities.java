package com.aetherianartificer.townstead.work;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The jobs a worksite can be told to prefer, and how to ask whether one has work waiting.
 *
 * <p>Work that produces an item can be ordered by naming the item. Work that does not — killing a
 * pig, dressing a carcass, mopping blood, walking a delivery — has nothing to name, so a line names
 * the <em>job</em> instead. For that to be more than an on/off switch, the list has to be able to
 * say "do this before that", and deciding it needs one thing each job can answer: is there any of
 * you to do right now?</p>
 *
 * <p>Tasks answer for themselves by registering a probe. The pattern is the codebase's own —
 * {@code SlaughterWorkTask} already stands aside for {@code CarcassWorkTask.hasActionableWork} —
 * generalised so the order of deference comes from the player's list rather than from which task
 * happened to name which other one.</p>
 */
public final class WorkActivities {

    /** Whether this job has anything to do for this worker right now. */
    public interface Probe {
        boolean hasWork(ServerLevel level, VillagerEntityMCA villager);
    }

    /** One task option composed from profession data and a code-owned availability probe. */
    public record Option(ResourceLocation id, String name, ResourceLocation icon) {}

    private record Entry(ResourceLocation id, Probe probe) {}

    private static final Map<ResourceLocation, Entry> ENTRIES = new ConcurrentHashMap<>();

    private WorkActivities() {}

    /** Register the live probes owned by bespoke task engines. Presentation stays in work data. */
    public static void bootstrap() {
        register(com.aetherianartificer.townstead.profession.def.WorkTaskTypes.SLAUGHTER,
                com.aetherianartificer.townstead.compat.butchery.SlaughterWorkTask::hasWorkWaiting);
        register(com.aetherianartificer.townstead.profession.def.WorkTaskTypes.DISMANTLE,
                com.aetherianartificer.townstead.compat.butchery.GolemProcessingTask::hasWorkWaiting);
        register(com.aetherianartificer.townstead.profession.def.WorkTaskTypes.HAMMER,
                com.aetherianartificer.townstead.compat.butchery.HeadHammeringTask::hasWorkWaiting);
        register(com.aetherianartificer.townstead.profession.def.WorkTaskTypes.CLEAN,
                com.aetherianartificer.townstead.compat.butchery.BloodCleanupTask::hasWorkWaiting);
        register(com.aetherianartificer.townstead.profession.def.WorkTaskTypes.BUTCHER,
                com.aetherianartificer.townstead.compat.butchery.CarcassWorkTask::hasActionableWork);
        register(com.aetherianartificer.townstead.profession.def.WorkTaskTypes.CURE,
                com.aetherianartificer.townstead.compat.butchery.SausageHookTask::hasWorkWaiting);
        register(com.aetherianartificer.townstead.profession.def.WorkTaskTypes.DELIVER,
                com.aetherianartificer.townstead.compat.butchery.ButcherDeliveryTask::hasWorkWaiting);
    }

    /**
     * Register the executable question for a task type. The probe is asked while deciding whether
     * a different job may start, so it must be read-only and no more expensive than the task's
     * own start condition. An {@code order} object in work data independently decides whether the
     * task appears on an order sheet.
     */
    public static void register(ResourceLocation id, Probe probe) {
        if (id == null || probe == null) return;
        ENTRIES.put(id, new Entry(id, probe));
    }

    /**
     * Whether this job can be listed and withheld at all.
     *
     * <p>False for the work that simply has to happen. Those never appear in a catalogue and are
     * never refused, including at a worksite told to stand down — otherwise telling a shop to rest
     * would strand its output where it was made.</p>
     */
    public static boolean isDiscretionary(@Nullable ResourceLocation id) {
        return orderOf(id) != null;
    }

    /** Whether this particular worksite declares the task as an orderable choice. */
    public static boolean isDiscretionary(ServerLevel level,
                                          com.aetherianartificer.townstead.work.site.Worksite site,
                                          @Nullable ResourceLocation id) {
        if (id == null || !ENTRIES.containsKey(id)) return false;
        var extent = com.aetherianartificer.townstead.work.site.Worksites.extentOf(level, site);
        for (var task : com.aetherianartificer.townstead.work.site.WorksiteWork
                .declaredTasksAt(level, site, extent, id)) {
            if (task.order() != null) return true;
        }
        return false;
    }

    /** The item a job borrows for its icon, since a job has no item of its own. */
    public static ResourceLocation iconOf(@Nullable ResourceLocation id) {
        var order = orderOf(id);
        return order == null ? null : order.icon();
    }

    /** Every declared job, in a stable order. */
    public static List<ResourceLocation> all() {
        List<ResourceLocation> out = new ArrayList<>(ENTRIES.keySet());
        out.sort(java.util.Comparator.comparing(ResourceLocation::toString));
        return List.copyOf(out);
    }

    /** The jobs worth offering at this place: discretionary, and relevant here. */
    public static List<Option> at(ServerLevel level,
                                  com.aetherianartificer.townstead.work.site.Worksite site) {
        List<Option> out = new ArrayList<>();
        var extent = com.aetherianartificer.townstead.work.site.Worksites.extentOf(level, site);
        List<ResourceLocation> types = new ArrayList<>(
                com.aetherianartificer.townstead.work.site.WorksiteWork.typesAt(level, site, extent));
        types.sort(java.util.Comparator.comparing(ResourceLocation::toString));
        for (ResourceLocation id : types) {
            if (!ENTRIES.containsKey(id)) continue;
            for (var task : com.aetherianartificer.townstead.work.site.WorksiteWork
                    .declaredTasksAt(level, site, extent, id)) {
                if (task.order() == null) continue;
                out.add(new Option(id, task.order().name(), task.order().icon()));
                break;
            }
        }
        return List.copyOf(out);
    }

    public static boolean isKnown(@Nullable ResourceLocation id) {
        return id != null && ENTRIES.containsKey(id);
    }

    /** What to call this job on screen. Falls back to the id's path so nothing renders blank. */
    public static String labelOf(@Nullable ResourceLocation id) {
        if (id == null) return "";
        var order = orderOf(id);
        return order != null ? order.name() : id.getPath().replace('_', ' ');
    }

    private static @Nullable com.aetherianartificer.townstead.profession.def.WorkTaskDef.OrderOption
    orderOf(@Nullable ResourceLocation id) {
        if (id == null || !ENTRIES.containsKey(id)) return null;
        for (var profession : com.aetherianartificer.townstead.profession.def.ProfessionDefs
                .all().values()) {
            for (var task : profession.workTasks()) {
                if (id.equals(task.type()) && task.order() != null) return task.order();
            }
        }
        return null;
    }

    /** Whether this job has work waiting. Unknown jobs report none, so they never outrank anything. */
    public static boolean hasWork(ServerLevel level, VillagerEntityMCA villager, @Nullable ResourceLocation id) {
        Entry entry = id == null ? null : ENTRIES.get(id);
        if (entry == null) return false;
        try {
            return entry.probe().hasWork(level, villager);
        } catch (Throwable ignored) {
            // A probe throwing must not take out the eligibility check that asked it.
            return false;
        }
    }

    /**
     * Whether a declared job above {@code activeTask} in the profession's data-authored weight
     * order has actionable work. Producer engines call this before claiming WALK_TARGET, so a
     * continuously available station cannot starve a carcass, grinder, delivery, or any future
     * higher-priority job that registered a probe.
     */
    public static boolean hasHigherPriorityWork(ServerLevel level, VillagerEntityMCA villager,
                                                ResourceLocation activeTask) {
        List<com.aetherianartificer.townstead.profession.def.WorkTaskDef> tasks =
                com.aetherianartificer.townstead.work.WorkTaskDeclarations.all(villager);
        int activeWeight = Integer.MIN_VALUE;
        for (var task : tasks) {
            if (task.type().equals(activeTask)) activeWeight = Math.max(activeWeight, task.weight());
        }
        if (activeWeight == Integer.MIN_VALUE) return false;
        for (var task : tasks) {
            if (task.weight() <= activeWeight) break;
            if (hasWork(level, villager, task.type())) return true;
        }
        return false;
    }
}
