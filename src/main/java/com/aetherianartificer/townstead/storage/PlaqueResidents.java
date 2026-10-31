package com.aetherianartificer.townstead.storage;

import com.aetherianartificer.townstead.compat.mca.McaRoomBinding;
import com.aetherianartificer.townstead.work.site.Worksite;
import com.aetherianartificer.townstead.work.site.WorksiteRegister;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Players living in a village through Ownership Plaques. A player listed on a home's plaque is a
 * resident of that home's village; lodging does not count. A player may live in any number.
 */
public final class PlaqueResidents {
    private PlaqueResidents() {}

    /** One home a player is listed on. */
    public record Home(ResourceLocation dimension, int villageId, String villageName, String placeName, BlockPos plaque) {}

    /** The players listed on homes in this village. */
    public static Set<UUID> players(ServerLevel level, Village village) {
        Set<UUID> out = new LinkedHashSet<>();
        if (level == null || village == null || level.getServer() == null) return out;
        for (Worksite site : WorksiteRegister.get(level.getServer()).all()) {
            if (!isHome(level, site, village)) continue;
            for (RoomOwner owner : site.owners()) {
                if (owner.kind() == RoomOwner.Kind.PLAYER) out.add(owner.uuid());
            }
        }
        return out;
    }

    /** Every home the player is listed on, across loaded dimensions. */
    public static List<Home> homes(MinecraftServer server, UUID player) {
        List<Home> out = new ArrayList<>();
        if (server == null || player == null) return out;
        for (Worksite site : WorksiteRegister.get(server).all()) {
            if (site.ownershipTag() == null || site.owners().stream().noneMatch(owner -> owner.uuid().equals(player))) continue;
            for (ServerLevel level : server.getAllLevels()) {
                if (!level.dimension().location().equals(site.key().dimension())) continue;
                Building building = McaRoomBinding.byId(level, site.key());
                Village village = building == null ? null : RoomOwnershipAccess.villageOf(level, building);
                if (village == null || BuildingLodging.isLodging(village, building)) continue;
                out.add(new Home(site.key().dimension(), village.getId(), village.getName(), site.name(), site.ownershipTag()));
            }
        }
        return out;
    }

    private static boolean isHome(ServerLevel level, Worksite site, Village village) {
        if (site.ownershipTag() == null || !site.ownershipPrivate()
                || !level.dimension().location().equals(site.key().dimension())) return false;
        Building building = McaRoomBinding.byId(level, site.key());
        if (building == null) return false;
        Village owner = RoomOwnershipAccess.villageOf(level, building);
        return owner != null && owner.getId() == village.getId() && !BuildingLodging.isLodging(village, building);
    }
}
