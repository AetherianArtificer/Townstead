package com.aetherianartificer.townstead.compat.farmersdelight;

import com.aetherianartificer.townstead.ai.work.WorkTaskDeclarations;
import com.aetherianartificer.townstead.compat.farmersdelight.cook.ModRecipeRegistry.StationType;
import com.aetherianartificer.townstead.compat.farmersdelight.cook.StationHandler.StationSlot;
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
public final class CookTaskDeclarations {

    public static final ResourceLocation COOK = WorkTaskTypes.COOK;
    public static final ResourceLocation CHOP = WorkTaskTypes.CHOP;
    public static final ResourceLocation BREW = WorkTaskTypes.BREW;

    private CookTaskDeclarations() {}

    /**
     * The villager's declared cook/chop tasks grouped into descending-weight buckets: the engine
     * tries each bucket's stations strictly before falling to the next, and equal weights share
     * one bucket (one merged pool, ranked by the engine). Empty when the profession declares no
     * cook-family work, in which case the engine must not run.
     */
    public static List<List<WorkTaskDef>> cookBuckets(VillagerEntityMCA villager) {
        return buckets(villager, COOK, CHOP);
    }

    /** Same contract as {@link #cookBuckets} for the barista beverage engine. */
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
                                       com.aetherianartificer.townstead.compat.farmersdelight.cook.ModRecipeRegistry.DiscoveredRecipe recipe) {
        ResourceLocation type = beveragesOnly ? BREW
                : stationType == StationType.CUTTING_BOARD ? CHOP : COOK;
        List<WorkTaskDef> tasks = WorkTaskDeclarations.declared(villager, type);
        if (tasks == null || tasks.isEmpty()) return true;
        for (WorkTaskDef task : tasks) {
            if (task.allowsRecipe(recipe.id(), recipe.output())) return true;
        }
        return false;
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

    private static List<List<WorkTaskDef>> buckets(VillagerEntityMCA villager,
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
