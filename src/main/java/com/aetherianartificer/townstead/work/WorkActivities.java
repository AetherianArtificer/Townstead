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

    /**
     * Whether this job is something the given place ever does.
     *
     * <p>Asked when building a worksite's catalogue. Without it every job is offered at every
     * worksite, and a kitchen's order screen lists slaughtering — which is not merely untidy, it
     * invites a player to write a line nobody there will ever work.</p>
     */
    public interface Relevance {
        boolean appliesTo(ServerLevel level,
                          com.aetherianartificer.townstead.work.site.Worksite site);
    }

    private record Entry(ResourceLocation id, String label, ResourceLocation icon,
                         boolean discretionary, Probe probe, Relevance relevance) {}

    private static final Map<ResourceLocation, Entry> ENTRIES = new ConcurrentHashMap<>();

    private WorkActivities() {}

    /**
     * Declares a job that can appear on an order list.
     *
     * <p>The probe is asked while deciding whether a <em>different</em> job may start, so it must
     * be as cheap as that task's own start condition and must not change anything.</p>
     */
    /**
     * @param discretionary whether a player would ever plausibly want this switched off. Carrying
     *                      finished goods to storage or dressing a carcass is not a choice, it is
     *                      how the work happens; killing somebody's animals is. Only discretionary
     *                      jobs are offered on a list, and only they can be withheld.
     */
    public static void register(ResourceLocation id, String label, ResourceLocation icon,
                                boolean discretionary, Probe probe, Relevance relevance) {
        if (id == null || probe == null) return;
        ENTRIES.put(id, new Entry(id, label == null || label.isEmpty() ? id.getPath() : label,
                icon, discretionary, probe,
                relevance == null ? (level, site) -> true : relevance));
    }

    /**
     * Whether this job can be listed and withheld at all.
     *
     * <p>False for the work that simply has to happen. Those never appear in a catalogue and are
     * never refused, including at a worksite told to stand down — otherwise telling a shop to rest
     * would strand its output where it was made.</p>
     */
    public static boolean isDiscretionary(@Nullable ResourceLocation id) {
        Entry entry = id == null ? null : ENTRIES.get(id);
        return entry != null && entry.discretionary();
    }

    /** The item a job borrows for its icon, since a job has no item of its own. */
    public static ResourceLocation iconOf(@Nullable ResourceLocation id) {
        Entry entry = id == null ? null : ENTRIES.get(id);
        return entry == null ? null : entry.icon();
    }

    /** Every declared job, in a stable order. */
    public static List<ResourceLocation> all() {
        List<ResourceLocation> out = new ArrayList<>(ENTRIES.keySet());
        out.sort(java.util.Comparator.comparing(ResourceLocation::toString));
        return List.copyOf(out);
    }

    /** The jobs worth offering at this place: discretionary, and relevant here. */
    public static List<ResourceLocation> at(ServerLevel level,
                                            com.aetherianartificer.townstead.work.site.Worksite site) {
        List<ResourceLocation> out = new ArrayList<>();
        for (ResourceLocation id : all()) {
            Entry entry = ENTRIES.get(id);
            if (entry == null || !entry.discretionary()) continue;
            try {
                if (entry.relevance().appliesTo(level, site)) out.add(id);
            } catch (Throwable ignored) {
                // A relevance check throwing must not empty the whole catalogue.
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
        Entry entry = ENTRIES.get(id);
        return entry != null ? entry.label() : id.getPath().replace('_', ' ');
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
}
