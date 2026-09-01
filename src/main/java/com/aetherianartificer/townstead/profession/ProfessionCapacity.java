package com.aetherianartificer.townstead.profession;

import com.aetherianartificer.townstead.compat.mca.McaBuildings;
import com.aetherianartificer.townstead.compat.mca.McaBuildingCompat;
import com.aetherianartificer.townstead.profession.def.JobSiteProvider;
import com.aetherianartificer.townstead.profession.def.ProfessionDef;
import com.aetherianartificer.townstead.profession.def.ProfessionDefs;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.npc.VillagerProfession;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Village workplace counting for careers whose def declares {@code via} surfaces. Capacity is
 * DESCRIPTIVE, never enforced: vanilla block logic stands — a claimed job block is a job — and
 * this class just counts honestly for the picker and the cook-site list. Every claimed via-POI
 * is a workable post (a pot inside a kitchen and a pot in a courtyard both employ someone);
 * building slots add the Townstead-assigned positions on top. Schema predicates live in
 * {@link com.aetherianartificer.townstead.profession.def.PoiHierarchy} (MCA-free, test-loadable).
 */
public final class ProfessionCapacity {

    private ProfessionCapacity() {}

    public static int capacity(ServerLevel level, Village village, ProfessionDef def) {
        return ProfessionSites.total(level, village, def);
    }

    private static final long POSTS_CACHE_TICKS = 100L;
    private record PostsCacheKey(String dimension, int villageId, ResourceLocation defId) {}
    /** One Townstead-managed standalone seat and the provider that authored it. */
    public record StandaloneSite(BlockPos post, int providerIndex) {}

    private record PostsCacheEntry(List<StandaloneSite> sites, long expiresAt) {}
    private static final java.util.concurrent.ConcurrentHashMap<PostsCacheKey, PostsCacheEntry> POSTS_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Declared job-block POIs standing OUTSIDE every building, in deterministic order. Via
     * surfaces use the named profession's POI; direct surfaces participate when the definition
     * also has building seats, forming its fallback acquisition route.
     * Indoor ones are deliberately absent: a building's own entry already seats workers by tier,
     * so counting its furniture again would put two workers on one pot. Cached per village for a
     * few seconds; eligibility asks at 20Hz and the POI query is far too heavy for that.
     */
    public static List<StandaloneSite> standaloneSites(
            ServerLevel level, Village village, ProfessionDef def) {
        PostsCacheKey key = new PostsCacheKey(
                level.dimension().location().toString(), village.getId(), def.id());
        long now = level.getGameTime();
        PostsCacheEntry cached = POSTS_CACHE.get(key);
        if (cached != null && now < cached.expiresAt()) return cached.sites();
        List<StandaloneSite> sites = collectStandaloneSites(level, village, def);
        POSTS_CACHE.put(key, new PostsCacheEntry(List.copyOf(sites), now + POSTS_CACHE_TICKS));
        return sites;
    }

    /**
     * Residents occupying this career's sites. A def declaring work tasks is occupied by every
     * profession sharing one of those task types (a Baker works the kitchen a Cook would;
     * a Beverage Artisan, declaring only brew, does not) — the same rule kitchen slot assignment uses.
     * A def without work tasks falls back to canonical-career identity.
     */
    public static int employed(ServerLevel level, Village village, ProfessionDef def) {
        ResourceLocation[] taskTypes = def.workTasks().stream()
                .map(com.aetherianartificer.townstead.profession.def.WorkTaskDef::type)
                .distinct()
                .toArray(ResourceLocation[]::new);
        int count = 0;
        for (VillagerEntityMCA resident : village.getResidents(level)) {
            VillagerProfession profession = resident.getVillagerData().getProfession();
            if (taskTypes.length > 0) {
                if (com.aetherianartificer.townstead.work.WorkTaskDeclarations
                        .professionDeclares(profession, taskTypes)) count++;
            } else {
                ResourceLocation id = BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession);
                if (id != null && def.id().equals(ProfessionDefs.canonicalId(id))) count++;
            }
        }
        return count;
    }

    public static Optional<Village> resolveVillage(VillagerEntityMCA villager) {
        Optional<Village> home = villager.getResidency().getHomeVillage();
        if (home.isPresent() && home.get().isWithinBorder(villager)) return home;
        Optional<Village> nearest = Village.findNearest(villager);
        if (nearest.isPresent() && nearest.get().isWithinBorder(villager)) return nearest;
        return Optional.empty();
    }

    /** The village's buildings whose type this def claims by prefix, in MCA iteration order. */
    public static List<Building> countedBuildings(Village village, ProfessionDef def) {
        List<JobSiteProvider.Building> providers = new ArrayList<>();
        for (JobSiteProvider provider : def.jobSites()) {
            if (provider instanceof JobSiteProvider.Building building) {
                providers.add(building);
            }
        }
        if (providers.isEmpty()) return List.of();
        List<Building> counted = new ArrayList<>();
        java.util.Map<Integer, String> effectiveTypes = McaBuildingCompat.effectiveTypes(village);
        for (Building building : McaBuildings.all(village)) {
            String type = effectiveTypes.get(building.getId());
            if (type != null && providers.stream().anyMatch(provider -> provider.matches(type))) {
                counted.add(building);
            }
        }
        return counted;
    }

    private static List<StandaloneSite> collectStandaloneSites(
            ServerLevel level, Village village, ProfessionDef def) {
        List<StandaloneSite> sites = new ArrayList<>();
        BlockPos center = new BlockPos(village.getCenter());
        PoiManager poiManager = level.getPoiManager();
        boolean hasBuildingProvider = def.jobSites().stream()
                .anyMatch(JobSiteProvider.Building.class::isInstance);
        // Two surfaces sharing a POI type must not double count the same physical block.
        Set<BlockPos> seen = new HashSet<>();
        for (int providerIndex = 0; providerIndex < def.jobSites().size(); providerIndex++) {
            JobSiteProvider provider = def.jobSites().get(providerIndex);
            if (!(provider instanceof JobSiteProvider.JobBlock block)) continue;

            // A plain job-block-only profession remains vanilla/MCA-owned. A direct block in a
            // building hierarchy is different: it is the standalone fallback after the building
            // seats, including feature POIs such as beehives whose max ticket count is zero.
            if (block.via() == null && !hasBuildingProvider) continue;
            VillagerProfession surface = professionById(
                    block.via() == null ? def.id() : block.via());
            if (surface == null || surface == VillagerProfession.NONE) continue;

            List<BlockPos> positions = poiManager.findAll(
                    surface.heldJobSite(),
                    p -> village.isWithinBorder(p, Village.BORDER_MARGIN),
                    center,
                    128, PoiManager.Occupancy.ANY)
                    .map(BlockPos::immutable)
                    .filter(pos -> block.blocks().contains(BuiltInRegistries.BLOCK.getKey(
                            level.getBlockState(pos).getBlock())))
                    .filter(pos -> !insideAnyBuilding(level, village, pos))
                    .filter(seen::add)
                    .sorted(java.util.Comparator.<BlockPos>comparingInt(BlockPos::getY)
                            .thenComparingInt(BlockPos::getZ)
                            .thenComparingInt(BlockPos::getX))
                    .toList();

            for (BlockPos post : groupedAnchors(positions, block.sitesPerWorker())) {
                sites.add(new StandaloneSite(post.immutable(), providerIndex));
            }
        }
        return sites;
    }

    /** First physical post in each authored sites-per-worker group becomes the seat anchor. */
    static List<BlockPos> groupedAnchors(List<BlockPos> positions, int sitesPerWorker) {
        if (positions == null || positions.isEmpty()) return List.of();
        int groupSize = Math.max(1, sitesPerWorker);
        List<BlockPos> anchors = new ArrayList<>((positions.size() + groupSize - 1) / groupSize);
        for (int i = 0; i < positions.size(); i += groupSize) anchors.add(positions.get(i));
        return List.copyOf(anchors);
    }

    /**
     * Whether this position stands inside one of the village's buildings — the one question that
     * separates a workplace from someone's kitchen furniture.
     */
    static boolean insideAnyBuilding(ServerLevel level, Village village, BlockPos pos) {
        return McaBuildingCompat.buildingAt(level, village, pos) != null;
    }

    private static VillagerProfession professionById(ResourceLocation id) {
        if (!BuiltInRegistries.VILLAGER_PROFESSION.containsKey(id)) return null;
        return BuiltInRegistries.VILLAGER_PROFESSION.get(id);
    }
}
