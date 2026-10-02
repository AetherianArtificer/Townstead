package com.aetherianartificer.townstead.root;

import com.aetherianartificer.townstead.root.building.BuildingSpawnPolicies;
import com.aetherianartificer.townstead.root.building.BuildingSpawnPolicy;
import com.aetherianartificer.townstead.root.disposition.DispositionGroups;
import com.aetherianartificer.townstead.root.disposition.RootPeace;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.Map;

/**
 * Spawn-time view of the local population a founder is appearing among, used to keep a settlement
 * coherent: it vetoes origins whose disposition group clashes with the dominant nearby group (so a
 * hostile-group villager never spawns into a settled area), and applies a spawner building's authored
 * origin policy ({@link BuildingSpawnPolicy}). {@link #active()} is false when no other MCA villagers
 * are nearby, leaving the normal biome-weighted selection untouched (wilderness founders seed freely).
 *
 * <p>The population is sampled by a plain radius query of loaded MCA villagers rather than MCA's
 * village registry, so it works regardless of whether the area is a recognized MCA village and is
 * immune to MCA village-API drift. The optional per-building policy still consults the MCA village,
 * guarded so any failure just drops the building policy (the disposition filter still runs).</p>
 */
public final class VillageSpawnContext {

    private static final VillageSpawnContext INACTIVE =
            new VillageSpawnContext(false, false, null, null, null, null);

    private final boolean active;
    private final boolean checkDispositions;
    private final BuildingSpawnPolicy buildingPolicy;   // nullable
    private final String majorityGroup;                 // dominant nearby group; nullable when none
    private final ResourceLocation majorityRoot;      // compatible fallback; nullable
    private final EntityType<?> bodyType;

    /** How far around the spawn to sample existing villagers when judging the local population. */
    private static final double RADIUS_XZ = 48.0;
    private static final double RADIUS_Y = 24.0;

    private VillageSpawnContext(boolean active, boolean checkDispositions, BuildingSpawnPolicy buildingPolicy,
                                String majorityGroup, ResourceLocation majorityRoot, EntityType<?> bodyType) {
        this.active = active;
        this.checkDispositions = checkDispositions;
        this.buildingPolicy = buildingPolicy;
        this.majorityGroup = majorityGroup;
        this.majorityRoot = majorityRoot;
        this.bodyType = bodyType;
    }

    public static VillageSpawnContext resolve(VillagerEntityMCA spawning) {
        if (!(spawning.level() instanceof ServerLevel server)) return INACTIVE;
        BlockPos pos = spawning.blockPosition();

        // Sample the loaded MCA villagers around the spawn; the plurality group/origin is the local
        // character. A plain radius query, NOT MCA's village registry, so it works whether or not the
        // area is a recognized village and can't be broken by MCA village-API drift.
        //
        // Group is resolved per distinct ROOT (cached in groupByRoot for this call), not per entity:
        // a town is mostly one origin, and DispositionGroups.of(entity) rebuilds the entity's whole
        // power set just to read its entity_group. ofRoot reads the origin's typical group instead
        // (identical while entity_group is a fixed origin grant). Originless villagers (rare) still use
        // the per-entity path.
        EntityType<?> bodyType = spawning.getType();
        Map<String, Integer> groupCounts = new HashMap<>();
        Map<String, Integer> originCounts = new HashMap<>();
        Map<String, String> groupByRoot = new HashMap<>();
        for (VillagerEntityMCA other : server.getEntitiesOfClass(VillagerEntityMCA.class,
                new AABB(pos).inflate(RADIUS_XZ, RADIUS_Y, RADIUS_XZ))) {
            if (other == spawning) continue;   // the new villager has no origin yet; never count it
            String rootId = TownsteadVillagers.get(other).life().rootId();
            String group;
            if (rootId != null && !rootId.isEmpty()) {
                originCounts.merge(rootId, 1, Integer::sum);
                group = groupByRoot.computeIfAbsent(rootId,
                        id -> DispositionGroups.ofRoot(ResourceLocation.tryParse(id), bodyType));
            } else {
                group = DispositionGroups.of(other);
            }
            groupCounts.merge(group, 1, Integer::sum);
        }
        String majorityGroup = plurality(groupCounts);
        if (majorityGroup == null) return INACTIVE;   // no neighbours: wilderness, seed freely

        BuildingSpawnPolicy policy = resolveBuildingPolicy(server, pos);
        boolean check = policy == null || policy.checkDispositions();
        String majorityRootId = plurality(originCounts);   // null when all neighbours are originless
        ResourceLocation majorityRoot = majorityRootId == null ? null : ResourceLocation.tryParse(majorityRootId);
        return new VillageSpawnContext(true, check, policy, majorityGroup, majorityRoot, bodyType);
    }

    /** Best-effort spawner-building policy from the MCA village at {@code pos}; guarded against drift. */
    private static BuildingSpawnPolicy resolveBuildingPolicy(ServerLevel server, BlockPos pos) {
        try {
            return VillageManager.get(server).findNearestVillage(pos, 0)
                    .flatMap(v -> v.getBuildingAt(pos)).map(Building::getType)
                    .map(BuildingSpawnPolicies::get).orElse(null);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Whether there is an established local population to keep coherent (else selection is unconstrained). */
    public boolean active() {
        return active;
    }

    /** Whether this candidate origin may be assigned here (building policy + village disposition compat). */
    public boolean allows(ResourceLocation rootId) {
        if (rootId == null) return false;
        if (buildingPolicy != null && !buildingPolicy.allows(rootId.toString())) return false;
        if (checkDispositions && majorityGroup != null) {
            String group = DispositionGroups.ofRoot(rootId, bodyType);
            if (DispositionGroups.clash(group, majorityGroup) && !RootPeace.shareVillage(rootId, majorityRoot)) return false;
        }
        return true;
    }

    /**
     * A guaranteed-compatible origin to fall back to when nothing in the weighted pool passes the
     * filter: the village's majority origin (it already lives here), or null to defer to the default.
     */
    public ResourceLocation fallbackRoot() {
        return majorityRoot != null && allows(majorityRoot) ? majorityRoot : null;
    }

    private static String plurality(Map<String, Integer> counts) {
        String best = null;
        int bestCount = 0;
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            if (e.getValue() > bestCount) {
                best = e.getKey();
                bestCount = e.getValue();
            }
        }
        return best;
    }
}
