package com.aetherianartificer.townstead.work.order;

import com.aetherianartificer.townstead.work.site.ProfessionWorksites;
import com.aetherianartificer.townstead.work.site.Worksite;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Which recipes one particular worksite may be asked to make, where that is the worksite's own knowledge rather than
 * its building type's: a recipe a player taught this shop and no other. A mod registers a {@link Gate} for the
 * recipes it owns; the Order Sheet offers, and the worker makes, only what every gate allows here.
 */
public final class WorksiteRecipeGates {

    public interface Gate {
        /** False to keep {@code recipeId} from this worksite, true to allow it, null for no opinion. */
        @Nullable Boolean allows(ServerLevel level, long worksiteId, ResourceLocation recipeId);
    }

    private static final List<Gate> GATES = new CopyOnWriteArrayList<>();

    private WorksiteRecipeGates() {}

    public static void register(Gate gate) {
        if (gate != null && !GATES.contains(gate)) GATES.add(gate);
    }

    static void clear() {
        GATES.clear();
    }

    /** Whether {@code site} may make {@code recipeId}. With no worksite, only recipes no gate claims. */
    public static boolean allows(ServerLevel level, @Nullable Worksite site, @Nullable ResourceLocation recipeId) {
        if (recipeId == null || GATES.isEmpty()) return true;
        long id = site == null ? -1L : site.id();
        for (Gate gate : GATES) {
            Boolean answer;
            try {
                answer = gate.allows(level, id, recipeId);
            } catch (RuntimeException e) {
                answer = Boolean.FALSE;
            }
            if (Boolean.FALSE.equals(answer)) return false;
        }
        return true;
    }

    /** The same, for the worksite {@code villager} works at. */
    public static boolean allowsAssigned(ServerLevel level, VillagerEntityMCA villager, @Nullable ResourceLocation recipeId) {
        if (recipeId == null || GATES.isEmpty()) return true;
        ProfessionWorksites.Assignment assignment = ProfessionWorksites.resolve(level, villager);
        return allows(level, assignment == null ? null : assignment.site(), recipeId);
    }
}
