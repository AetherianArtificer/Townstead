package com.aetherianartificer.townstead.storage;

import com.aetherianartificer.townstead.compat.mca.McaBuildingCompat;
import com.aetherianartificer.townstead.compat.mca.McaBuildings;
import com.aetherianartificer.townstead.compat.mca.McaRoomBinding;
import com.aetherianartificer.townstead.work.site.Worksite;
import com.aetherianartificer.townstead.work.site.WorksiteKey;
import com.aetherianartificer.townstead.work.site.WorksiteRegister;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * What an Ownership Plaque allows. A plaque lists the people a place belongs to; villagers MCA
 * houses inside count as listed. While it lists anyone, it keeps everyone else out of the place's
 * storage, and the building type decides what it means for beds: in a home only listed villagers
 * may live, in lodging (an inn) anyone may sleep a night. Players are never stopped by a plaque.
 */
public final class RoomOwnershipAccess {
    private RoomOwnershipAccess() {}

    /** The plaque governing a place, the building it hangs in, and whether its beds are lodging. */
    public record Place(ServerLevel level, @Nullable Village village, Building building, Worksite site,
                        OwnershipScope scope, boolean lodging) {
        public boolean restricts() {
            return site.ownershipPrivate();
        }

        public List<Building> rooms() {
            return McaBuildingCompat.rooms(village, building, scope == OwnershipScope.BUILDING);
        }

        /** Whether the person is named on the plaque, or is a villager MCA houses here. */
        public boolean lists(UUID person, @Nullable VillagerEntityMCA villager) {
            if (site.owners().stream().anyMatch(owner -> owner.uuid().equals(person))) return true;
            return villager != null && mcaHomeIsInside(level, villager, building, scope);
        }
    }

    public static @Nullable Place placeAt(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null || level.getServer() == null) return null;
        Building building = McaBuildingCompat.buildingAt(level, pos);
        return building == null ? null : placeOf(level, building);
    }

    static @Nullable Place placeOf(ServerLevel level, Building building) {
        Policy policy = policyFor(level, building);
        if (policy == null) return null;
        Village village = villageOf(level, building);
        return new Place(level, village, building, policy.site(), policy.scope(),
                BuildingLodging.isLodging(village, building));
    }

    /** Whether a villager may use the storage and work blocks at {@code target}. */
    public static boolean mayAccess(ServerLevel level, VillagerEntityMCA actor, BlockPos target) {
        if (actor == null) return true;
        Place place = placeAt(level, target);
        return place == null || !place.restricts() || place.lists(actor.getUUID(), actor);
    }

    /**
     * Whether a villager may sleep a night in this bed without it becoming their home. Lodging
     * beds are open unless a plaque on that very room lists people.
     */
    public static boolean maySleep(ServerLevel level, VillagerEntityMCA actor, BlockPos bed) {
        if (actor == null) return true;
        Place place = placeAt(level, bed);
        if (place == null || !place.restricts()) return true;
        if (place.lodging() && place.scope() == OwnershipScope.BUILDING) return true;
        return place.lists(actor.getUUID(), actor);
    }

    /** Whether MCA may give this villager the bed as their home. Lodging is left to MCA. */
    public static boolean mayClaimHome(ServerLevel level, VillagerEntityMCA actor, BlockPos bed) {
        if (actor == null) return true;
        Place place = placeAt(level, bed);
        if (place == null || !place.restricts() || place.lodging()) return true;
        return place.lists(actor.getUUID(), actor);
    }

    /** Whether this position is in a place a plaque keeps people out of. */
    public static boolean isPrivate(ServerLevel level, BlockPos target) {
        Place place = placeAt(level, target);
        return place != null && place.restricts();
    }

    private static Worksite siteFor(ServerLevel level, Building building) {
        WorksiteKey key = new WorksiteKey(
                McaRoomBinding.ID, level.dimension().location(), building.getId());
        return WorksiteRegister.get(level.getServer()).find(key);
    }

    static boolean mcaHomeIsInside(ServerLevel level, VillagerEntityMCA villager, Building building) {
        return mcaHomeIsInside(level, villager, building, OwnershipScope.ROOM);
    }

    static boolean mcaHomeIsInside(ServerLevel level, VillagerEntityMCA villager, Building building,
                                   OwnershipScope scope) {
        Optional<GlobalPos> home = villager.getResidency().getHome();
        if (home.isEmpty() || !home.get().dimension().equals(level.dimension())) return false;
        Building homeBuilding = McaBuildingCompat.functionalRoomAt(level, home.get().pos());
        if (homeBuilding == null) return false;
        if (scope != OwnershipScope.BUILDING) return homeBuilding.getId() == building.getId();
        Village village = villageOf(level, building);
        return village != null && McaBuildingCompat.sameWholeBuilding(village, building, homeBuilding);
    }

    static Policy policyFor(ServerLevel level, Building building) {
        if (level == null || building == null || level.getServer() == null) return null;
        Worksite room = siteFor(level, building);
        if (room != null && room.ownershipTag() != null
                && room.ownershipScope() == OwnershipScope.ROOM) {
            return new Policy(room, OwnershipScope.ROOM);
        }

        Village village = villageOf(level, building);
        if (village == null) return null;
        for (Worksite candidate : WorksiteRegister.get(level.getServer()).all()) {
            if (candidate.ownershipTag() == null
                    || candidate.ownershipScope() != OwnershipScope.BUILDING
                    || !candidate.key().dimension().equals(level.dimension().location())
                    || !McaRoomBinding.ID.equals(candidate.key().binding())) continue;
            Building anchor = McaRoomBinding.byId(level, candidate.key());
            if (anchor != null && McaBuildingCompat.sameWholeBuilding(village, building, anchor)) {
                return new Policy(candidate, OwnershipScope.BUILDING);
            }
        }
        return null;
    }

    static Village villageOf(ServerLevel level, Building building) {
        if (level == null || building == null) return null;
        for (Village village : VillageManager.get(level)) {
            if (McaBuildings.byId(village, building.getId()) != null) return village;
        }
        return null;
    }

    record Policy(Worksite site, OwnershipScope scope) {}
}
