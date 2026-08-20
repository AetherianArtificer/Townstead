package com.aetherianartificer.townstead.work.site;

import com.aetherianartificer.townstead.compat.mca.McaRoomBinding;
import com.aetherianartificer.townstead.profession.def.JobSiteProvider;
import com.aetherianartificer.townstead.profession.def.ProfessionDef;
import com.aetherianartificer.townstead.profession.def.ProfessionDefs;
import com.aetherianartificer.townstead.profession.def.WorkTaskDef;

import net.conczin.mca.server.world.data.Building;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.HashSet;
import java.util.Set;

/**
 * What kinds of work happen at a place, read from the professions that claim it.
 *
 * <p>A worksite's stations say what is <em>possible</em> there, which is not the same as what is
 * done there: a butcher's shop with a furnace in it could smelt anything, and offering its whole
 * furnace catalogue was how a butchery came to be asked whether it wanted to bake.</p>
 *
 * <p>The answer already exists in data. Every profession declares its {@code poi} — a building
 * type prefix, a job block, or nothing at all — and its {@code work_tasks}. So the trades that work
 * here are the ones whose poi this place satisfies, and the work done here is theirs. A pack adds a
 * profession with a poi and some work tasks and its work appears at that building, with no new
 * schema and nothing hardcoded.</p>
 *
 * <p>A place no profession claims is not a workplace, and nobody being there to work is the same
 * answer as having nothing to order: the set comes back empty.</p>
 */
public final class WorksiteWork {

    private WorksiteWork() {}

    /** The work-task types the trades claiming this place declare. Empty means nobody works here. */
    public static Set<ResourceLocation> typesAt(ServerLevel level, Worksite site, Set<Long> extent) {
        Set<ResourceLocation> types = new HashSet<>();
        for (ProfessionDef def : claimants(level, site, extent)) {
            for (WorkTaskDef task : def.workTasks()) types.add(task.type());
        }
        return types;
    }

    /** Careers the building declares as possible workers, in data-defined order. */
    public static java.util.List<ProfessionDef> professionsAt(
            ServerLevel level, Worksite site, Set<Long> extent) {
        return java.util.List.copyOf(claimants(level, site, extent));
    }

    /**
     * The claiming trades' declarations of one task type, filters and all. This is how the
     * catalogue narrows a recipe family to what the trades here may actually make: the crafting
     * family is every recipe in the game, and "what an armorer's bench offers" is the armorer's
     * own {@code recipes} filter, not the family's.
     */
    public static java.util.List<WorkTaskDef> declaredTasksAt(
            ServerLevel level, Worksite site, Set<Long> extent, ResourceLocation type) {
        java.util.List<WorkTaskDef> out = new java.util.ArrayList<>();
        for (ProfessionDef def : claimants(level, site, extent)) {
            for (WorkTaskDef task : def.workTasks()) {
                if (task.type().equals(type)) out.add(task);
            }
        }
        return out;
    }

    /**
     * The trades that claim this place. A trade that names this building TYPE owns the place,
     * and owners outrank visitors: a cafe holds a skillet, the skillet is the cook's job block,
     * and without this rule the cook's whole menu landed on the barista's sheet. Job-block
     * claims only speak where no trade owns the type — a lone smoker in an unclassified room
     * is still a butcher's corner.
     */
    private static java.util.List<ProfessionDef> claimants(ServerLevel level, Worksite site, Set<Long> extent) {
        String buildingType = buildingTypeOf(level, site);
        // An explicit building workforce owns the answer. This is what lets a Mill advertise
        // itself to both Bakers and Cooks without either profession hardcoding every mill added
        // by every content pack. Their task declarations still decide which of its stations and
        // recipes each worker can actually use.
        if (BuildingWorkforceIndex.defines(buildingType)) {
            java.util.List<ProfessionDef> declared = new java.util.ArrayList<>();
            for (ResourceLocation profession : BuildingWorkforceIndex.professionsFor(buildingType)) {
                ProfessionDef def = ProfessionDefs.byId(profession);
                if (def != null && !declared.contains(def)) declared.add(def);
            }
            return declared;
        }
        java.util.List<ProfessionDef> owners = new java.util.ArrayList<>();
        for (ProfessionDef def : ProfessionDefs.all().values()) {
            if (claimsByType(def, buildingType)) owners.add(def);
        }
        if (!owners.isEmpty()) return owners;

        Set<ResourceLocation> blocks = blocksIn(level, extent);
        for (ProfessionDef def : ProfessionDefs.all().values()) {
            if (claimsByBlock(def, blocks)) owners.add(def);
        }
        return owners;
    }

    /** Whether a profession names this building type as its own kind of place. */
    private static boolean claimsByType(ProfessionDef def, String buildingType) {
        if (buildingType == null) return false;
        for (JobSiteProvider provider : def.jobSites()) {
            if (!(provider instanceof JobSiteProvider.Building building)) continue;
            for (String prefix : building.typePrefixes()) {
                if (!prefix.isEmpty() && buildingType.startsWith(prefix)) return true;
            }
        }
        return false;
    }

    /** Whether a profession's job block stands here. Only asked where nobody owns the type. */
    private static boolean claimsByBlock(ProfessionDef def, Set<ResourceLocation> blocks) {
        for (JobSiteProvider provider : def.jobSites()) {
            if (!(provider instanceof JobSiteProvider.JobBlock jobBlock)) continue;
            for (ResourceLocation block : jobBlock.blocks()) {
                if (blocks.contains(block)) return true;
            }
            // townstead:always means the trade needs no site, which is not a claim on this one.
        }
        return false;
    }

    private static String buildingTypeOf(ServerLevel level, Worksite site) {
        Building building = McaRoomBinding.byId(level, site.key());
        return building == null ? null : building.getType();
    }

    /** The distinct blocks standing inside the worksite, for job-block claims to match against. */
    private static Set<ResourceLocation> blocksIn(ServerLevel level, Set<Long> extent) {
        Set<ResourceLocation> out = new HashSet<>();
        for (long packed : extent) {
            out.add(BuiltInRegistries.BLOCK.getKey(
                    level.getBlockState(BlockPos.of(packed)).getBlock()));
        }
        return out;
    }
}
