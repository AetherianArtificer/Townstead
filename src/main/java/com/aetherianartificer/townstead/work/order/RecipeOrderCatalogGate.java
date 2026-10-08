package com.aetherianartificer.townstead.work.order;

import com.aetherianartificer.townstead.profession.def.WorkTaskDef;
import com.aetherianartificer.townstead.work.recipe.DiscoveredRecipe;
import com.aetherianartificer.townstead.work.recipe.StationType;
import com.aetherianartificer.townstead.work.recipe.WorkRecipeRegistry;
import com.aetherianartificer.townstead.work.station.WorkstationDef;
import com.aetherianartificer.townstead.work.station.Workstations;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Shared station-and-recipe ownership rules for discovered-recipe order catalogues. */
final class RecipeOrderCatalogGate {

    private RecipeOrderCatalogGate() {}

    /** Exact recipe owners first; vanilla role-owned families resolve through the present defs. */
    static List<WorkstationDef> matchingStations(
            StationType type, DiscoveredRecipe recipe, List<WorkstationDef> recipeDefs,
            Set<ResourceLocation> presentDefs) {
        List<WorkstationDef> out = new ArrayList<>();
        for (WorkstationDef def : recipeDefs) {
            if (presentDefs.contains(def.id())) out.add(def);
        }
        if (!recipeDefs.isEmpty()) return List.copyOf(out);

        ResourceLocation recipeType = WorkRecipeRegistry.recipeTypeId(recipe);
        for (ResourceLocation id : presentDefs) {
            WorkstationDef def = Workstations.byId(id);
            if (def == null || def.role() != type) continue;
            // A declared family is exact. Adapter-owned roles such as campfires name no family
            // and remain eligible for the registry recipes already assigned to their role.
            if (def.recipeType() != null && !def.recipeType().equals(recipeType)) continue;
            out.add(def);
        }
        return List.copyOf(out);
    }

    /** A claiming work declaration must cover this exact station and admit this exact recipe. */
    static boolean allowedByAny(
            List<WorkTaskDef> declarations, WorkstationDef station, DiscoveredRecipe recipe) {
        for (WorkTaskDef task : declarations) {
            if (!taskDrives(task, station)) continue;
            if (task.allowsRecipe(recipe.id(), recipe.output(), recipe.inputs())) return true;
        }
        return false;
    }

    private static boolean taskDrives(WorkTaskDef task, WorkstationDef station) {
        if (task.anyWorkstation()) return true;
        for (ResourceLocation block : station.blocks()) {
            if (task.allowsBlock(block)) return true;
        }
        for (ResourceLocation tagId : station.blockTags()) {
            TagKey<Block> tag = TagKey.create(Registries.BLOCK, tagId);
            for (var holder : BuiltInRegistries.BLOCK.getTagOrEmpty(tag)) {
                ResourceLocation block = BuiltInRegistries.BLOCK.getKey(holder.value());
                if (task.allowsBlock(block)) return true;
            }
        }
        return false;
    }
}
