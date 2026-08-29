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

import java.util.Optional;

/** The single access policy for Townstead systems which let villagers use a place. */
public final class RoomOwnershipAccess {
    private RoomOwnershipAccess() {}

    /**
     * A room deed overrides a whole-building deed; otherwise rooms inherit their structure's deed.
     * Open/private is independent of the list of named people, so a private deed with no names is
     * residents-only. Players are intentionally not intercepted here—the deed governs villager
     * automation rather than becoming a multiplayer claim system.
     */
    public static boolean mayAccess(ServerLevel level, VillagerEntityMCA actor, BlockPos target) {
        if (level == null || actor == null || target == null || level.getServer() == null) return true;
        Building building = McaBuildingCompat.buildingAt(level, target);
        if (building == null) return true;
        Policy policy = policyFor(level, building);
        if (policy == null || !policy.site().ownershipPrivate()) return true;

        if (policy.site().owners().stream().anyMatch(owner -> owner.uuid().equals(actor.getUUID()))) {
            return true;
        }
        return mcaHomeIsInside(level, actor, building, policy.scope());
    }

    /**
     * Bed use is narrower than ordinary access. A private deed with named people reserves its
     * beds for the named villagers; being a resident of the surrounding home does not also grant
     * a bed in somebody else's private room. A private deed with nobody named retains its
     * residents-only meaning.
     */
    public static boolean maySleep(ServerLevel level, VillagerEntityMCA actor, BlockPos bed) {
        if (level == null || actor == null || bed == null || level.getServer() == null) return true;
        Building building = McaBuildingCompat.buildingAt(level, bed);
        if (building == null) return true;
        Policy policy = policyFor(level, building);
        if (policy == null || !policy.site().ownershipPrivate()) return true;

        if (policy.site().owners().stream().anyMatch(owner -> owner.uuid().equals(actor.getUUID()))) {
            return true;
        }
        if (!policy.site().owners().isEmpty()) return false;
        return mcaHomeIsInside(level, actor, building, policy.scope());
    }

    /** Whether this position belongs to a room governed by a private deed. */
    public static boolean isPrivate(ServerLevel level, BlockPos target) {
        if (level == null || target == null || level.getServer() == null) return false;
        Building building = McaBuildingCompat.buildingAt(level, target);
        Policy policy = building == null ? null : policyFor(level, building);
        return policy != null && policy.site().ownershipPrivate();
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
