package com.aetherianartificer.townstead.work.site;

import com.aetherianartificer.townstead.profession.ProfessionCapacity;
import com.aetherianartificer.townstead.profession.def.ProfessionDef;
import com.aetherianartificer.townstead.profession.def.ProfessionDefs;
import com.aetherianartificer.townstead.compat.mca.McaBuildings;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Which building a villager works, answered from their profession def alone.
 *
 * <p>Every trade used to re-derive this for itself: the cook and the barista each carry a private
 * copy of the same algorithm, differing only in a hardcoded building-type prefix. The algorithm
 * itself was never trade-specific — the def already names the building types it claims, so any
 * profession's workplace can be resolved the same way: the villager's village, the village's
 * claimed buildings in a deterministic order, and workers paired to them by sorted identity so
 * two smiths in a two-forge village each keep their own forge across re-resolutions.</p>
 *
 * <p>Workers beyond the building count share by wrap-around rather than idling: a claimed job is
 * a job, and a second armorer at a one-forge village belongs at the forge, not in the street.
 * Station claims arbitrate the actual blocks.</p>
 */
public final class ProfessionWorksites {

    /** A resolved workplace: the room, its worksite record, and a reference position inside it. */
    public record Assignment(Building building, Worksite site, BlockPos reference) {}

    private ProfessionWorksites() {}

    /** The building this villager works, or null when their def claims none the village has. */
    @Nullable
    public static Assignment resolve(ServerLevel level, VillagerEntityMCA villager) {
        ProfessionDef def = defOf(villager);
        if (def == null) return null;
        Village village = ProfessionCapacity.resolveVillage(villager).orElse(null);
        if (village == null) return null;

        List<Building> buildings = new ArrayList<>(ProfessionCapacity.countedBuildings(village, def));
        if (buildings.isEmpty()) return null;
        // The def's poi order is a preference: an armorer lists armorer, armory, blacksmith,
        // so their own room fills before the shared smithy. A shared building (the blacksmith,
        // last in every smith's list) thereby offers one seat per trade — each trade counts it
        // among its own buildings and sends someone only after its more specific rooms are
        // spoken for. Position only breaks ties.
        List<String> prefixes = prefixOrder(def);
        buildings.sort(Comparator
                .<Building>comparingInt(b -> prefixRank(prefixes, b))
                .thenComparingInt(b -> center(b).getY())
                .thenComparingInt(b -> center(b).getZ())
                .thenComparingInt(b -> center(b).getX()));

        int index = workerIndex(level, village, def, villager);
        Building building = buildings.get(Math.floorMod(index, buildings.size()));
        Worksite site = Worksites.of(level, building);
        if (site == null) return null;
        return new Assignment(building, site, referenceOf(building, villager));
    }

    /**
     * Every secondary building this villager may service. The building opts professions in via
     * its {@code extended_buildings} {@code workers} list; the villager's assignment policy then
     * either accepts all of those (automatic) or filters them through the player's manual list.
     */
    public static List<Assignment> additional(ServerLevel level, VillagerEntityMCA villager) {
        return additional(level, villager, resolve(level, villager));
    }

    private static List<Assignment> additional(ServerLevel level, VillagerEntityMCA villager,
                                               @Nullable Assignment primary) {
        ProfessionDef def = defOf(villager);
        if (def == null) return List.of();
        Village village = ProfessionCapacity.resolveVillage(villager).orElse(null);
        if (village == null) return List.of();

        var policy = TownsteadVillagers.get(villager).worksiteAssignments();
        List<Building> buildings = new ArrayList<>();
        for (Building building : McaBuildings.all(village)) {
            if (BuildingWorkforceIndex.accepts(building.getType(), def.id())) buildings.add(building);
        }
        buildings.sort(Comparator
                .comparingInt((Building b) -> center(b).getY())
                .thenComparingInt(b -> center(b).getZ())
                .thenComparingInt(b -> center(b).getX())
                .thenComparing(Building::getType));

        List<Assignment> out = new ArrayList<>();
        for (Building building : buildings) {
            Worksite site = Worksites.of(level, building);
            if (site == null) continue;
            if (primary != null && primary.site().key().equals(site.key())) continue;
            if (!policy.permitsAdditional(site.id())) continue;
            out.add(new Assignment(building, site, referenceOf(building, villager)));
        }
        return List.copyOf(out);
    }

    /** Primary employment first, followed by the compatible secondary sites. */
    public static List<Assignment> all(ServerLevel level, VillagerEntityMCA villager) {
        List<Assignment> out = new ArrayList<>();
        Assignment primary = resolve(level, villager);
        if (primary != null) out.add(primary);
        out.addAll(additional(level, villager, primary));
        return List.copyOf(out);
    }

    /**
     * The site a producer should service next. Pending secondary order sheets beat an idle primary
     * site; ties use distance and stable worksite id. With no requested secondary work the worker
     * remains at their primary employment site, so automatic assignment does not cause wandering.
     */
    @Nullable
    public static Assignment resolveForWork(ServerLevel level, VillagerEntityMCA villager) {
        Assignment primary = resolve(level, villager);
        List<Assignment> requested = new ArrayList<>();
        for (Assignment assignment : additional(level, villager, primary)) {
            if (hasPendingOrders(level, villager, assignment.site())) requested.add(assignment);
        }
        if (requested.isEmpty()) return primary;
        requested.sort(Comparator
                .comparingDouble((Assignment a) -> villager.distanceToSqr(
                        a.reference().getX() + 0.5, a.reference().getY() + 0.5,
                        a.reference().getZ() + 0.5))
                .thenComparingLong(a -> a.site().id()));
        return requested.get(0);
    }

    private static boolean hasPendingOrders(ServerLevel level, VillagerEntityMCA villager,
                                            Worksite site) {
        if (site.orders().isEmpty()) return false;
        var context = com.aetherianartificer.townstead.work.order.WorksiteOrders
                .contextFor(level, site, villager);
        for (var order : site.orders().orders()) {
            if (!order.isActivity() && context.mayWork(order) && order.wantsWork(context)) return true;
        }
        return false;
    }

    /** The def's building-type prefixes in declaration order — most specific claim first. */
    private static List<String> prefixOrder(ProfessionDef def) {
        List<String> out = new ArrayList<>();
        for (var provider : def.jobSites()) {
            if (provider instanceof com.aetherianartificer.townstead.profession.def
                    .JobSiteProvider.Building building) {
                out.addAll(building.typePrefixes());
            }
        }
        return out;
    }

    private static int prefixRank(List<String> prefixes, Building building) {
        String type = building.getType();
        if (type == null) return Integer.MAX_VALUE;
        for (int i = 0; i < prefixes.size(); i++) {
            String prefix = prefixes.get(i);
            if (!prefix.isEmpty() && type.startsWith(prefix)) return i;
        }
        return Integer.MAX_VALUE;
    }

    /** The assignment's extent through the register's cached path; empty when unresolvable. */
    public static Set<Long> extentOf(ServerLevel level, Assignment assignment) {
        return Worksites.extentOf(level, assignment.site(), assignment.building(), null);
    }

    /**
     * This villager's stable position among the village's workers of the same profession,
     * ordered by UUID exactly as the cook and barista assignments order theirs. Same
     * PROFESSION, not same task types: trades may share the craft/smelt vocabulary while
     * making entirely different things, and a weaponsmith's existence must not shift which
     * building the armorer gets — each trade distributes its own people over its own claimed
     * buildings, which is what lets a shared building seat one of each. A non-resident (fresh
     * arrival, carried villager) sorts in by the same key rather than being refused a
     * workplace.
     */
    private static int workerIndex(ServerLevel level, Village village, ProfessionDef def,
                                   VillagerEntityMCA villager) {
        List<UUID> workers = new ArrayList<>();
        boolean seen = false;
        for (VillagerEntityMCA resident : village.getResidents(level)) {
            if (!sameDef(resident, def)) continue;
            workers.add(resident.getUUID());
            if (resident.getUUID().equals(villager.getUUID())) seen = true;
        }
        if (!seen) workers.add(villager.getUUID());
        workers.sort(Comparator.comparing(UUID::toString));
        return workers.indexOf(villager.getUUID());
    }

    private static boolean sameDef(VillagerEntityMCA resident, ProfessionDef def) {
        ProfessionDef other = defOf(resident);
        return other != null && other.id().equals(def.id());
    }

    @Nullable
    private static ProfessionDef defOf(VillagerEntityMCA villager) {
        ResourceLocation id = BuiltInRegistries.VILLAGER_PROFESSION
                .getKey(villager.getVillagerData().getProfession());
        return id == null ? null : ProfessionDefs.byId(id);
    }

    private static BlockPos center(Building building) {
        BlockPos center = building.getCenter();
        return center != null ? center : BlockPos.ZERO;
    }

    private static BlockPos referenceOf(Building building, VillagerEntityMCA villager) {
        BlockPos center = building.getCenter();
        if (center != null) return center;
        Optional<BlockPos> first = building.getBlockPosStream().findFirst();
        return first.map(BlockPos::immutable).orElse(villager.blockPosition());
    }
}
