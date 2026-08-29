package com.aetherianartificer.townstead.work.producer;

import com.aetherianartificer.townstead.work.station.StationTypeCoverage;

import com.aetherianartificer.townstead.work.recipe.DiscoveredRecipe;
import com.aetherianartificer.townstead.work.recipe.StationType;

import com.aetherianartificer.townstead.work.WorkTaskDeclarations;
import com.aetherianartificer.townstead.work.station.Stations.StationSlot;
import com.aetherianartificer.townstead.profession.def.WorkTaskDef;
import com.aetherianartificer.townstead.profession.def.WorkTaskTypes;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Reads a villager's profession-declared {@code work_tasks} for the cook family. One producer
 * state machine serves the whole family; the declarations decide which stations it may use and
 * in what preference order. {@code townstead_work:chop} covers cutting boards;
 * {@code townstead_work:cook} and {@code townstead_work:brew} cover every other station kind.
 * The def's workstation block lists are the real gate. Declarations are the whole gate: a
 * profession works these engines exactly when its def (directly or via aliases) declares the
 * types.
 */
public final class ProducerTaskDeclarations {

    public static final ResourceLocation COOK = WorkTaskTypes.COOK;
    public static final ResourceLocation CHOP = WorkTaskTypes.CHOP;
    public static final ResourceLocation BREW = WorkTaskTypes.BREW;

    private ProducerTaskDeclarations() {}

    /**
     * The villager's declared cook/chop tasks grouped into descending-weight buckets: the engine
     * tries each bucket's stations strictly before falling to the next, and equal weights share
     * one bucket (one merged pool, ranked by the engine). Empty when the profession declares no
     * cook-family work, in which case the engine must not run.
     */
    public static List<List<WorkTaskDef>> cookBuckets(VillagerEntityMCA villager) {
        return buckets(villager, COOK, CHOP);
    }

    /** Same contract as {@link #cookBuckets} for the beverage-production engine. */
    public static List<List<WorkTaskDef>> brewBuckets(VillagerEntityMCA villager) {
        return buckets(villager, BREW, null);
    }

    /**
     * Recipe gate for candidate selection: some declared task covering this station type must
     * allow the recipe (by recipe id, output item id, or output item {@code #tag}). With no
     * covering declaration the recipe passes; the station gates already decided access.
     */
    public static boolean allowsRecipe(VillagerEntityMCA villager, StationType stationType,
                                       boolean beveragesOnly,
                                       DiscoveredRecipe recipe) {
        ResourceLocation type = beveragesOnly ? BREW
                : stationType == StationType.CUTTING_BOARD ? CHOP : COOK;
        return allowsRecipe(villager, stationType, recipe, type);
    }

    /** Recipe gate for an arbitrary discovered-production task family. */
    public static boolean allowsRecipe(VillagerEntityMCA villager, StationType stationType,
                                       DiscoveredRecipe recipe, ResourceLocation... taskTypes) {
        List<WorkTaskDef> tasks = WorkTaskDeclarations.declared(villager, taskTypes);
        if (tasks == null || tasks.isEmpty()) return true;
        return allowsRecipe(tasks, stationType, recipe);
    }

    static boolean allowsRecipe(List<WorkTaskDef> tasks, StationType stationType,
                                DiscoveredRecipe recipe) {
        boolean governed = false;
        for (WorkTaskDef task : tasks) {
            if (!governsStationType(task, stationType)) continue;
            governed = true;
            if (task.allowsRecipe(recipe.id(), recipe.output(), recipe.inputs())) return true;
        }
        return !governed;
    }

    /**
     * Only tasks that govern this station kind get a say. A profession declaring several tasks of
     * one family scopes each to its own stations, so an open recipe set on the cookware task
     * cannot vouch for what happens at a furnace.
     */
    static boolean allowsRecipe(List<WorkTaskDef> tasks, StationType stationType,
                                @Nullable ResourceLocation recipeId, @Nullable ResourceLocation outputId) {
        boolean governed = false;
        for (WorkTaskDef task : tasks) {
            if (!governsStationType(task, stationType)) continue;
            governed = true;
            if (task.allowsRecipe(recipeId, outputId)) return true;
        }
        // No task speaks for this station kind: the station gates already decided access, so the
        // recipe passes rather than dying in an allowlist nobody wrote.
        return !governed;
    }

    /**
     * Whether a task's declared workstations include any block of this station kind. A task that
     * names no workstations governs everything, which is the existing default-open contract.
     */
    private static boolean governsStationType(WorkTaskDef task, StationType stationType) {
        if (task.anyWorkstation()) return true;
        return StationTypeCoverage.of(task).contains(stationType);
    }

    /**
     * How far this bucket may search. Tasks in a bucket share one station search, so the widest
     * declared scope wins; the narrower tasks are still held to their own block lists by
     * {@link #stationFilter}.
     */
    public static WorkTaskDef.Scope scopeOf(List<WorkTaskDef> bucket) {
        WorkTaskDef.Scope scope = WorkTaskDef.Scope.WORKSITE;
        for (WorkTaskDef task : bucket) {
            scope = scope.widest(task.scope());
        }
        return scope;
    }

    /** Station gate for one weight bucket. */
    public static Predicate<StationSlot> stationFilter(List<WorkTaskDef> bucket) {
        return slot -> {
            for (WorkTaskDef task : bucket) {
                if (taskCoversStation(task.type(), slot.type()) && task.allowsBlock(slot.blockId())) {
                    return true;
                }
            }
            return false;
        };
    }

    /**
     * Which declaration family governs a station: cutting boards belong to chop, every other
     * station kind belongs to cook/brew — the same default-open rule {@link #allowsRecipe}
     * uses, so a new station kind works by default instead of dying in an allowlist. The
     * def's workstation block list remains the real gate.
     */
    private static boolean taskCoversStation(ResourceLocation taskType, StationType stationType) {
        return (stationType == StationType.CUTTING_BOARD) == CHOP.equals(taskType);
    }

    public static List<List<WorkTaskDef>> buckets(VillagerEntityMCA villager,
                                                   ResourceLocation first,
                                                   @Nullable ResourceLocation second) {
        List<WorkTaskDef> declared = second == null
                ? WorkTaskDeclarations.declared(villager, first)
                : WorkTaskDeclarations.declared(villager, first, second);
        if (declared == null) return List.of();

        List<List<WorkTaskDef>> buckets = new ArrayList<>();
        for (WorkTaskDef task : declared) {
            if (buckets.isEmpty()
                    || buckets.get(buckets.size() - 1).get(0).weight() != task.weight()) {
                buckets.add(new ArrayList<>());
            }
            buckets.get(buckets.size() - 1).add(task);
        }
        return buckets;
    }
}
