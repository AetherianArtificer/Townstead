package com.aetherianartificer.townstead.compat.farmersdelight;

import com.aetherianartificer.townstead.work.recipe.StationType;

import com.aetherianartificer.townstead.data.ReloadGeneration;
import com.aetherianartificer.townstead.work.station.WorkstationDef;
import com.aetherianartificer.townstead.work.station.Workstations;
import com.aetherianartificer.townstead.profession.def.WorkTaskDef;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which station kinds a work task's declared workstations amount to.
 *
 * <p>Resolving a block list (and any block tags in it) against the workstation registry is not
 * something the recipe gate can afford per candidate recipe, and the answer only changes when
 * datapacks reload — the same event that rebuilds both the workstation defs and the recipes. So it
 * is computed once per task and dropped on the next reload.</p>
 */
final class StationTypeCoverage {

    private static final Map<WorkTaskDef, Set<StationType>> CACHE = new ConcurrentHashMap<>();
    private static volatile int cachedGeneration = -1;

    private StationTypeCoverage() {}

    static Set<StationType> of(WorkTaskDef task) {
        int generation = ReloadGeneration.current();
        if (cachedGeneration != generation) {
            CACHE.clear();
            cachedGeneration = generation;
        }
        return CACHE.computeIfAbsent(task, StationTypeCoverage::resolve);
    }

    private static Set<StationType> resolve(WorkTaskDef task) {
        EnumSet<StationType> types = EnumSet.noneOf(StationType.class);
        WorkTaskDef.TargetSet stations = task.workstations();
        for (ResourceLocation blockId : stations.ids()) {
            WorkstationDef def = Workstations.byBlockId(blockId);
            if (def != null) types.add(def.role());
        }
        for (ResourceLocation tagId : stations.tags()) {
            TagKey<Block> tag = TagKey.create(Registries.BLOCK, tagId);
            for (var holder : BuiltInRegistries.BLOCK.getTagOrEmpty(tag)) {
                WorkstationDef def = Workstations.byState(holder.value().defaultBlockState());
                if (def != null) types.add(def.role());
            }
        }
        return types;
    }
}
