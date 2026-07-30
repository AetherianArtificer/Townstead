package com.aetherianartificer.townstead.work;

import com.aetherianartificer.townstead.profession.def.ProfessionDef;
import com.aetherianartificer.townstead.profession.def.ProfessionDefs;
import com.aetherianartificer.townstead.profession.def.WorkTaskDef;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Reads a villager's profession-declared {@code work_tasks} for any task engine. Any profession
 * may declare any registered type, so pack-defined careers compose from the full engine library.
 * Declarations ARE the gate: an engine runs for exactly the professions whose def declares its
 * type. There is no hardcoded-profession fallback; a profession without a def (or without the
 * declaration) simply does not run the engine.
 */
public final class WorkTaskDeclarations {

    private WorkTaskDeclarations() {}

    /**
     * The positive gate for engines: true when the villager's profession declares an available
     * task of one of these types. Runs in brain eligibility checks (every villager, every work
     * tick), so it scans without allocating.
     */
    public static boolean permitsTask(VillagerEntityMCA villager, ResourceLocation... types) {
        ProfessionDef def = defOf(villager);
        if (def == null) return false;
        for (WorkTaskDef task : def.workTasks()) {
            for (ResourceLocation type : types) {
                if (task.type().equals(type) && task.available(villager)) return true;
            }
        }
        return false;
    }

    /**
     * Profession-only form for contexts without a villager entity (vanilla villagers, scans).
     * Per-task {@code requirements} are not evaluated here since there is no one to test.
     */
    public static boolean professionDeclares(@Nullable net.minecraft.world.entity.npc.VillagerProfession profession,
                                             ResourceLocation... types) {
        if (profession == null) return false;
        ProfessionDef def = ProfessionDefs.byId(BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession));
        if (def == null) return false;
        for (WorkTaskDef task : def.workTasks()) {
            for (ResourceLocation type : types) {
                if (task.type().equals(type)) return true;
            }
        }
        return false;
    }

    /**
     * The villager's declared, currently-available tasks of the given types, sorted by
     * descending weight. Null when the profession has no def or declares nothing; engines treat
     * that the same as empty (the engine does not run).
     */
    public static @Nullable List<WorkTaskDef> declared(VillagerEntityMCA villager,
                                                       ResourceLocation... types) {
        ProfessionDef def = defOf(villager);
        if (def == null || def.workTasks().isEmpty()) return null;
        List<WorkTaskDef> out = new ArrayList<>();
        for (WorkTaskDef task : def.workTasks()) {
            for (ResourceLocation type : types) {
                if (task.type().equals(type)) {
                    if (task.available(villager)) out.add(task);
                    break;
                }
            }
        }
        out.sort(Comparator.comparingInt(WorkTaskDef::weight).reversed());
        return out;
    }

    /** The highest-weight declared entry of this type, for engines that read parameters; null when not declared. */
    public static @Nullable WorkTaskDef first(VillagerEntityMCA villager, ResourceLocation type) {
        ProfessionDef def = defOf(villager);
        if (def == null || def.workTasks().isEmpty()) return null;
        WorkTaskDef best = null;
        for (WorkTaskDef task : def.workTasks()) {
            if (!task.type().equals(type) || !task.available(villager)) continue;
            if (best == null || task.weight() > best.weight()) best = task;
        }
        return best;
    }

    private static @Nullable ProfessionDef defOf(VillagerEntityMCA villager) {
        ResourceLocation professionId = BuiltInRegistries.VILLAGER_PROFESSION
                .getKey(villager.getVillagerData().getProfession());
        return ProfessionDefs.byId(professionId);
    }
}
