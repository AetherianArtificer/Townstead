package com.aetherianartificer.townstead.profession;

import com.aetherianartificer.townstead.compat.mca.McaBuildings;
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
    private record PostsCacheEntry(List<BlockPos> posts, long expiresAt) {}
    private static final java.util.concurrent.ConcurrentHashMap<PostsCacheKey, PostsCacheEntry> POSTS_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Declared {@code via} POIs standing OUTSIDE every building, in deterministic order — each
     * is a post in its own right, the pot in the courtyard rather than the pot in the kitchen.
     * Indoor ones are deliberately absent: a building's own entry already seats workers by tier,
     * so counting its furniture again would put two workers on one pot. Cached per village for a
     * few seconds; eligibility asks at 20Hz and the POI query is far too heavy for that.
     */
    public static List<BlockPos> standalonePois(ServerLevel level, Village village, ProfessionDef def) {
        PostsCacheKey key = new PostsCacheKey(
                level.dimension().location().toString(), village.getId(), def.id());
        long now = level.getGameTime();
        PostsCacheEntry cached = POSTS_CACHE.get(key);
        if (cached != null && now < cached.expiresAt()) return cached.posts();
        List<BlockPos> posts = collectViaPois(level, village, def);
        POSTS_CACHE.put(key, new PostsCacheEntry(List.copyOf(posts), now + POSTS_CACHE_TICKS));
        return posts;
    }

    /**
     * Residents occupying this career's sites. A def declaring work tasks is occupied by every
     * profession sharing one of those task types (a Baker works the kitchen a Cook would;
     * a Barista, declaring only brew, does not) — the same rule kitchen slot assignment uses.
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
        List<String> prefixes = new ArrayList<>();
        for (JobSiteProvider provider : def.jobSites()) {
            if (provider instanceof JobSiteProvider.Building building) {
                prefixes.addAll(building.typePrefixes());
            }
        }
        if (prefixes.isEmpty()) return List.of();
        List<Building> counted = new ArrayList<>();
        for (Building building : McaBuildings.all(village)) {
            String type = building.getType();
            if (type != null && prefixes.stream().anyMatch(type::startsWith)) counted.add(building);
        }
        return counted;
    }

    private static List<BlockPos> collectViaPois(ServerLevel level, Village village, ProfessionDef def) {
        List<BlockPos> posts = new ArrayList<>();
        BlockPos center = new BlockPos(village.getCenter());
        PoiManager poiManager = level.getPoiManager();
        // Two surfaces sharing a POI type must not double count the same block.
        Set<BlockPos> seen = new HashSet<>();
        for (JobSiteProvider provider : def.jobSites()) {
            if (!(provider instanceof JobSiteProvider.JobBlock block) || block.via() == null) continue;
            VillagerProfession via = professionById(block.via());
            if (via == null || via == VillagerProfession.NONE) continue;
            for (BlockPos pos : poiManager.findAll(
                    via.heldJobSite(),
                    p -> village.isWithinBorder(p, Village.BORDER_MARGIN),
                    center,
                    128,
                    PoiManager.Occupancy.ANY).toList()) {
                BlockPos immutable = pos.immutable();
                if (!seen.add(immutable)) continue;
                // Filtered here rather than at the call site so the answer rides the posts
                // cache: containment is a walk of every building, and the sites list is
                // rebuilt on ticks where a villager is only walking to work.
                if (insideAnyBuilding(village, immutable)) continue;
                posts.add(immutable);
            }
        }
        posts.sort(java.util.Comparator.<BlockPos>comparingInt(BlockPos::getY)
                .thenComparingInt(BlockPos::getZ).thenComparingInt(BlockPos::getX));
        return posts;
    }

    /**
     * Whether this position stands inside one of the village's buildings — the one question that
     * separates a workplace from someone's kitchen furniture.
     */
    static boolean insideAnyBuilding(Village village, BlockPos pos) {
        for (Building building : McaBuildings.all(village)) {
            if (building.containsPos(pos)) return true;
        }
        return false;
    }

    private static VillagerProfession professionById(ResourceLocation id) {
        if (!BuiltInRegistries.VILLAGER_PROFESSION.containsKey(id)) return null;
        return BuiltInRegistries.VILLAGER_PROFESSION.get(id);
    }
}
