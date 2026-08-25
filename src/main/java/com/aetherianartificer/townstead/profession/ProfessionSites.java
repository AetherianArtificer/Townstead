package com.aetherianartificer.townstead.profession;

import com.aetherianartificer.townstead.compat.mca.McaBuildings;
import com.aetherianartificer.townstead.profession.def.JobSiteProvider;
import com.aetherianartificer.townstead.profession.def.ProfessionDef;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Every place a career can be worked in one village, in a stable order.
 *
 * <p>This was the cook's private machinery ({@code buildCookSites} and friends) until a second
 * tiered trade needed it and a third was obviously coming. Nothing here knows what a kitchen is:
 * a def's {@code poi} entries say which buildings seat workers and how many, and the same walk
 * answers for a café, a smithy or anything a pack declares.</p>
 *
 * <h2>The list is an ordered claiming hierarchy</h2>
 *
 * <p>{@link JobSiteProvider}'s contract already called the {@code poi} list "an ordered
 * acquisition hierarchy"; this is where that becomes true. Entries claim sites in list order and
 * <strong>a place already claimed by an earlier entry is never counted again</strong>. Precedence
 * is positional rather than a web of "this one cancels that one" rules, because pairwise
 * exclusions are what rot as trades are added.</p>
 *
 * <p>The rule that falls out: <strong>a station inside a building is not a site.</strong> A
 * building's own entry already priced the room by tier, so counting the pot standing in it too
 * would seat two cooks on one pot and make the tier ladder bypassable by placing pots. A station
 * inside someone's <em>house</em> is not a workplace either — nobody should be able to hire a
 * villager into your kitchen at home. Only stations standing outside every building are posts.</p>
 */
public final class ProfessionSites {

    private ProfessionSites() {}

    /**
     * One unit of employment: a seat in a building, or a standalone post. Exactly one of the two
     * is set. {@code providerIndex} records which {@code poi} entry granted it, which assignment
     * needs (to know what kind of place a worker was sent to) and counting does not.
     */
    public record Site(@Nullable Building building, @Nullable BlockPos post, int providerIndex) {

        public boolean isBuilding() {
            return building != null;
        }
    }

    /** How many workers this career can employ in this village. */
    public static int total(ServerLevel level, Village village, @Nullable ProfessionDef def) {
        return sites(level, village, def).size();
    }

    /**
     * The career that owns this work task, or null when none declares it.
     *
     * <p>More than one def can declare the same task — a Baker works the kitchen a Cook would —
     * so this is decided rather than taken in map order: a career anyone can simply practice
     * beats a gated one, then the lowest id wins. It has to be deterministic because it picks
     * whose building prefixes and slot ladder are read, and an unstable answer there would move
     * seats around between runs.</p>
     */
    public static @Nullable ProfessionDef defForTask(net.minecraft.resources.ResourceLocation taskType) {
        if (taskType == null) return null;
        ProfessionDef best = null;
        for (ProfessionDef def : com.aetherianartificer.townstead.profession.def.ProfessionDefs
                .all().values()) {
            boolean declares = false;
            for (com.aetherianartificer.townstead.profession.def.WorkTaskDef task : def.workTasks()) {
                if (taskType.equals(task.type())) { declares = true; break; }
            }
            if (!declares) continue;
            if (best == null || prefer(def, best)) best = def;
        }
        return best;
    }

    /** Practiced beats gated; then lowest id, so registry map order never decides. */
    private static boolean prefer(ProfessionDef candidate, ProfessionDef current) {
        if (candidate.isRoot() != current.isRoot()) return candidate.isRoot();
        return candidate.id().compareTo(current.id()) < 0;
    }

    /**
     * The registered profession an auto-promotion should assign for this work task. Null when
     * the career exists only as data — nothing to put on a villager.
     */
    public static @Nullable net.minecraft.world.entity.npc.VillagerProfession professionForTask(
            net.minecraft.resources.ResourceLocation taskType) {
        ProfessionDef def = defForTask(taskType);
        if (def == null) return null;
        if (!net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION.containsKey(def.id())) {
            return null;
        }
        net.minecraft.world.entity.npc.VillagerProfession profession =
                net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION.get(def.id());
        return profession == net.minecraft.world.entity.npc.VillagerProfession.NONE ? null : profession;
    }

    /**
     * The site this villager works, or empty when the village has no room for them.
     *
     * <p>Sites and workers are both deterministically ordered and worker N takes site N, so every
     * caller and every tick agrees without storing an assignment anywhere. Who counts as a worker
     * is the def's own work tasks, which is what lets a Baker fill a kitchen seat a Cook would
     * have taken while a Barista, declaring only brewing, does not.</p>
     */
    public static Optional<Site> assignedSite(ServerLevel level, VillagerEntityMCA villager,
                                              @Nullable ProfessionDef def) {
        if (def == null) return Optional.empty();
        Optional<Village> villageOpt = ProfessionCapacity.resolveVillage(villager);
        if (villageOpt.isEmpty()) return Optional.empty();
        Village village = villageOpt.get();
        if (!isMember(village, villager)) return Optional.empty();

        List<Site> sites = sites(level, village, def);
        if (sites.isEmpty()) return Optional.empty();

        int index = workers(level, village, def, villager).indexOf(villager.getUUID());
        if (index < 0 || index >= sites.size()) return Optional.empty();
        return Optional.of(sites.get(index));
    }

    /**
     * The site this worker should actively service. Building-authored secondary assignments with
     * pending orders take precedence; the older seat resolver remains the fallback for standalone
     * job posts that are not MCA buildings.
     */
    public static Optional<Site> serviceSite(ServerLevel level, VillagerEntityMCA villager,
                                             @Nullable ProfessionDef fallbackDef) {
        com.aetherianartificer.townstead.work.site.ProfessionWorksites.Assignment assignment =
                com.aetherianartificer.townstead.work.site.ProfessionWorksites
                        .resolveForWork(level, villager);
        if (assignment != null) return Optional.of(new Site(assignment.building(), null, -1));
        return assignedSite(level, villager, fallbackDef);
    }

    /**
     * The walkable extent of the site this villager works — the room discovered from the world,
     * not MCA's furniture-only geometry, since standing, arrival and "in stock here" all read it.
     * A standalone post anchors the same flood fill on its own block.
     */
    public static Set<Long> extentOf(ServerLevel level, VillagerEntityMCA villager,
                                     @Nullable ProfessionDef def) {
        Optional<Site> site = assignedSite(level, villager, def);
        if (site.isEmpty()) {
            // Direct vanilla-style POIs are explicitly assigned in JOB_SITE memory rather than
            // synthesized as MCA building seats. The work area around that anchor may contain
            // several authored sites, such as a group of hives serviced by one beekeeper.
            Optional<net.minecraft.core.GlobalPos> memory = villager.getBrain()
                    .getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE);
            if (def != null && memory.isPresent()
                    && memory.get().dimension().equals(level.dimension())
                    && matchesDirectJobBlock(level, memory.get().pos(), def)) {
                return com.aetherianartificer.townstead.work.WorkSiteBounds
                        .workAreaAround(level, memory.get().pos());
            }
            return Set.of();
        }
        Building building = site.get().building();
        return building != null
                ? com.aetherianartificer.townstead.work.WorkSiteBounds.workArea(level, building)
                : com.aetherianartificer.townstead.work.WorkSiteBounds
                        .workAreaAround(level, site.get().post());
    }

    private static boolean matchesDirectJobBlock(ServerLevel level, BlockPos pos,
                                                 ProfessionDef def) {
        net.minecraft.resources.ResourceLocation blockId = net.minecraft.core.registries
                .BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
        for (JobSiteProvider provider : def.jobSites()) {
            if (provider instanceof JobSiteProvider.JobBlock block
                    && block.via() == null && block.blocks().contains(blockId)) return true;
        }
        return false;
    }

    /** Whether this villager's worksite contains any of these blocks. */
    public static boolean worksiteContainsAny(ServerLevel level, VillagerEntityMCA villager,
                                              @Nullable ProfessionDef def,
                                              List<net.minecraft.resources.ResourceLocation> blockIds) {
        for (long packed : extentOf(level, villager, def)) {
            BlockPos pos = BlockPos.of(packed);
            net.minecraft.resources.ResourceLocation id = net.minecraft.core.registries
                    .BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
            if (blockIds.contains(id)) return true;
        }
        return false;
    }

    /** Whether this village could employ one more of this career. */
    public static boolean hasFreeSite(ServerLevel level, VillagerEntityMCA villager,
                                      @Nullable ProfessionDef def) {
        if (def == null) return false;
        Optional<Village> villageOpt = ProfessionCapacity.resolveVillage(villager);
        if (villageOpt.isEmpty()) return false;
        Village village = villageOpt.get();
        if (!isMember(village, villager)) return false;
        int sites = sites(level, village, def).size();
        return sites > 0 && ProfessionCapacity.employed(level, village, def) < sites;
    }

    /**
     * This career's workers in a stable order, with the asking villager folded in when they do
     * this work — a villager being considered for a seat has to appear in the ordering that
     * decides seats, or they could never be given one.
     */
    private static List<java.util.UUID> workers(ServerLevel level, Village village, ProfessionDef def,
                                                VillagerEntityMCA asking) {
        net.minecraft.resources.ResourceLocation[] taskTypes = def.workTasks().stream()
                .map(com.aetherianartificer.townstead.profession.def.WorkTaskDef::type)
                .distinct()
                .toArray(net.minecraft.resources.ResourceLocation[]::new);
        List<java.util.UUID> ids = new ArrayList<>();
        for (VillagerEntityMCA resident : village.getResidents(level)) {
            if (!declares(resident, taskTypes)) continue;
            if (!ids.contains(resident.getUUID())) ids.add(resident.getUUID());
        }
        if (declares(asking, taskTypes) && !ids.contains(asking.getUUID())) ids.add(asking.getUUID());
        ids.sort(Comparator.comparing(java.util.UUID::toString));
        return ids;
    }

    private static boolean declares(VillagerEntityMCA villager,
                                    net.minecraft.resources.ResourceLocation[] taskTypes) {
        return taskTypes.length > 0 && com.aetherianartificer.townstead.work.WorkTaskDeclarations
                .professionDeclares(villager.getVillagerData().getProfession(), taskTypes);
    }

    /**
     * Whether this villager belongs to this village — inside its border AND on its books. The
     * cook has always asked for both; a passer-by standing in the square is not staff.
     */
    private static boolean isMember(Village village, VillagerEntityMCA villager) {
        if (!village.isWithinBorder(villager)) return false;
        Optional<Village> home = villager.getResidency().getHomeVillage();
        if (home.isPresent() && home.get().getId() == village.getId()) return true;
        java.util.UUID id = villager.getUUID();
        return village.getResidentsUUIDs().anyMatch(id::equals);
    }

    /**
     * Every site, deterministically ordered: buildings first (by height, then position), then
     * standalone posts. Order is the assignment itself — worker N takes site N — so it must not
     * depend on iteration order of anything unsorted, or villagers would swap workplaces between
     * runs.
     */
    public static List<Site> sites(ServerLevel level, Village village, @Nullable ProfessionDef def) {
        if (def == null || def.jobSites().isEmpty()) return List.of();
        List<Site> sites = new ArrayList<>();
        List<Building> claimed = new ArrayList<>();

        // Pass one: buildings. They run first regardless of where they sit in the list, because
        // a post only knows whether it stands outside a building once every building is known.
        for (int i = 0; i < def.jobSites().size(); i++) {
            if (!(def.jobSites().get(i) instanceof JobSiteProvider.Building provider)) continue;
            for (Building building : sortedBuildings(village, provider)) {
                claimed.add(building);
                int seats = provider.slotsFor(building.getType());
                for (int seat = 0; seat < seats; seat++) {
                    sites.add(new Site(building, null, i));
                }
            }
        }

        // Pass two: standalone posts. The indoor ones are already gone — the posts query drops
        // anything inside a building so the answer rides its cache (see the class note on why
        // indoor stations are not sites at all).
        //
        // Self-declared StationPost entries contribute nothing yet: there is no cheap indexed
        // way to find arbitrary blocks in a village, and a POI type's block set is fixed at
        // registry time so it cannot be driven from a datapack tag. The shape is parsed and
        // ordered here so the data is settled ahead of that mechanism.
        int postProvider = firstPostProvider(def);
        if (postProvider >= 0) {
            for (BlockPos post : ProfessionCapacity.standalonePois(level, village, def)) {
                sites.add(new Site(null, post.immutable(), postProvider));
            }
        }
        return List.copyOf(sites);
    }

    /** The entry posts are attributed to: the first that can produce one, or -1 if none can. */
    private static int firstPostProvider(ProfessionDef def) {
        for (int i = 0; i < def.jobSites().size(); i++) {
            JobSiteProvider provider = def.jobSites().get(i);
            if (provider instanceof JobSiteProvider.JobBlock block && block.via() != null) return i;
            if (provider instanceof JobSiteProvider.StationPost) return i;
        }
        return -1;
    }

    /**
     * The buildings one entry speaks for, lowest and most north-westerly first. This ordering is
     * load-bearing: it decides which building each worker is assigned to, so changing it
     * reshuffles who works where in existing worlds.
     */
    private static List<Building> sortedBuildings(Village village, JobSiteProvider.Building provider) {
        List<Building> matches = new ArrayList<>();
        for (Building building : McaBuildings.all(village)) {
            if (provider.matches(building.getType())) matches.add(building);
        }
        matches.sort(ProfessionSites::compareBuildings);
        return matches;
    }

    private static int compareBuildings(Building a, Building b) {
        BlockPos ac = a.getCenter();
        BlockPos bc = b.getCenter();
        if (ac != null && bc != null) {
            if (ac.getY() != bc.getY()) return Integer.compare(ac.getY(), bc.getY());
            if (ac.getZ() != bc.getZ()) return Integer.compare(ac.getZ(), bc.getZ());
            if (ac.getX() != bc.getX()) return Integer.compare(ac.getX(), bc.getX());
        } else if (ac != null) {
            return -1;
        } else if (bc != null) {
            return 1;
        }
        return a.getType().compareTo(b.getType());
    }
}
