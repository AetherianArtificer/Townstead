package com.aetherianartificer.townstead.work.site;

import com.aetherianartificer.townstead.profession.ProfessionCapacity;
import com.aetherianartificer.townstead.profession.def.ProfessionDef;
import com.aetherianartificer.townstead.profession.def.ProfessionDefs;
import com.aetherianartificer.townstead.compat.mca.McaBuildings;
import com.aetherianartificer.townstead.compat.mca.McaBuildingCompat;
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

/**
 * Which building a villager works, answered from their profession def alone.
 *
 * <p>Every trade used to re-derive this for itself: cooking and beverage work each carried a private
 * copy of the same algorithm, differing only in a hardcoded building-type prefix. The algorithm
 * itself was never trade-specific — the def already names the building types it claims, so any
 * profession's workplace can be resolved the same way: the villager's village, the village's
 * claimed building seats in deterministic order, and workers paired to them through the shared
 * profession-site resolver. That resolver preserves sorted identity while giving a committed
 * Path first claim on building families carrying its {@code path_affinity}.</p>
 *
 * <p>Capacity remains the building definition's decision. A tier that seats two workers exposes
 * two seats; excess workers do not silently manufacture another position.</p>
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
        com.aetherianartificer.townstead.profession.ProfessionSites.Site assigned =
                com.aetherianartificer.townstead.profession.ProfessionSites
                        .assignedSite(level, villager, def).orElse(null);
        if (assigned == null || assigned.building() == null) return null;
        Building building = assigned.building();
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
        var effectiveTypes = McaBuildingCompat.effectiveTypes(village);
        List<Building> buildings = new ArrayList<>();
        for (Building building : McaBuildings.all(village)) {
            String rawType = building.getType();
            String effectiveType = effectiveTypes.getOrDefault(building.getId(), rawType);
            boolean acceptsWorker = BuildingWorkforceIndex.accepts(effectiveType, villager)
                    || (!java.util.Objects.equals(effectiveType, rawType)
                    && BuildingWorkforceIndex.accepts(rawType, villager));
            if (!acceptsWorker) continue;

            // A profession-owned building already contributes a finite number of real seats.
            // Its broad `workers` declaration advertises who can use its Order Sheet; it must not
            // turn the same building into an unlimited secondary workplace for every villager of
            // that profession. Only the worker assigned through ProfessionSites services it.
            if (isDeclaredBuildingSite(def.jobSites(), rawType, effectiveType)) continue;
            buildings.add(building);
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

    static boolean isDeclaredBuildingSite(
                                          List<com.aetherianartificer.townstead.profession.def.JobSiteProvider> jobSites,
                                          @Nullable String rawType,
                                          @Nullable String effectiveType) {
        for (var provider : jobSites) {
            if (provider instanceof com.aetherianartificer.townstead.profession.def.JobSiteProvider.Building building
                    && (building.matches(rawType) || building.matches(effectiveType))) {
                return true;
            }
        }
        return false;
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

    /** The assignment's extent through the register's cached path; empty when unresolvable. */
    public static Set<Long> extentOf(ServerLevel level, Assignment assignment) {
        return Worksites.extentOf(level, assignment.site(), assignment.building(), null);
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
