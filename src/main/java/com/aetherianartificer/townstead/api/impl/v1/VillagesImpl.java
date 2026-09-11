package com.aetherianartificer.townstead.api.impl.v1;

import com.aetherianartificer.townstead.api.v1.VillagesApi;
import com.aetherianartificer.townstead.api.v1.model.BuildingSnapshot;
import com.aetherianartificer.townstead.api.v1.model.SpiritSnapshot;
import com.aetherianartificer.townstead.api.v1.model.VillageId;
import com.aetherianartificer.townstead.api.v1.model.VillageNeedsSummary;
import com.aetherianartificer.townstead.api.v1.model.VillageSnapshot;
import com.aetherianartificer.townstead.calendar.WorldCalendarSavedData;
import com.aetherianartificer.townstead.compat.mca.McaBuildingCompat;
import com.aetherianartificer.townstead.compat.mca.McaBuildings;
import com.aetherianartificer.townstead.spirit.SpiritReadout;
import com.aetherianartificer.townstead.spirit.SpiritRegistry;
import com.aetherianartificer.townstead.spirit.SpiritTotals;
import com.aetherianartificer.townstead.spirit.VillageSpiritAggregator;
import com.aetherianartificer.townstead.spirit.VillageSpiritCache;
import com.aetherianartificer.townstead.village.ResidentRegister;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

final class VillagesImpl implements VillagesApi {

    @Override
    public Optional<VillageSnapshot> village(MinecraftServer server, VillageId id) {
        try {
            ServerLevel level = ApiSupport.levelOf(server, id);
            if (level == null) return Optional.empty();
            return ApiSupport.findVillage(server, id).map(v -> snapshot(level, v));
        } catch (Throwable t) {
            ApiSupport.swallow("villages.village", t);
            return Optional.empty();
        }
    }

    @Override
    public Optional<VillageSnapshot> village(ServerLevel level, int villageId) {
        try {
            if (level == null) return Optional.empty();
            return VillageManager.get(level).getOrEmpty(villageId).map(v -> snapshot(level, v));
        } catch (Throwable t) {
            ApiSupport.swallow("villages.village", t);
            return Optional.empty();
        }
    }

    @Override
    public Optional<VillageSnapshot> nearest(ServerLevel level, BlockPos pos) {
        try {
            if (level == null || pos == null) return Optional.empty();
            return VillageManager.get(level).findNearestVillage(pos, Village.MERGE_MARGIN)
                    .map(v -> snapshot(level, v));
        } catch (Throwable t) {
            ApiSupport.swallow("villages.nearest", t);
            return Optional.empty();
        }
    }

    @Override
    public List<VillageSnapshot> all(ServerLevel level) {
        List<VillageSnapshot> out = new ArrayList<>();
        try {
            if (level == null) return out;
            for (Village village : VillageManager.get(level)) {
                out.add(snapshot(level, village));
            }
        } catch (Throwable t) {
            ApiSupport.swallow("villages.all", t);
        }
        return out;
    }

    @Override
    public Optional<VillageId> homeOf(Entity entity) {
        return ApiSupport.homeOf(entity);
    }

    @Override
    public Optional<SpiritSnapshot> spirit(MinecraftServer server, VillageId id) {
        try {
            ServerLevel level = ApiSupport.levelOf(server, id);
            if (level == null) return Optional.empty();
            return ApiSupport.findVillage(server, id).map(v -> spirit(level, v));
        } catch (Throwable t) {
            ApiSupport.swallow("villages.spirit", t);
            return Optional.empty();
        }
    }

    @Override
    public List<BuildingSnapshot> buildings(MinecraftServer server, VillageId id) {
        List<BuildingSnapshot> out = new ArrayList<>();
        try {
            Optional<Village> village = ApiSupport.findVillage(server, id);
            if (village.isEmpty()) return out;
            for (Building building : McaBuildings.all(village.get())) {
                BuildingSnapshot snapshot = building(id, village.get(), building);
                if (snapshot != null) out.add(snapshot);
            }
        } catch (Throwable t) {
            ApiSupport.swallow("villages.buildings", t);
        }
        return out;
    }

    @Override
    public Optional<BuildingSnapshot> buildingAt(ServerLevel level, BlockPos pos) {
        try {
            if (level == null || pos == null) return Optional.empty();
            Optional<Village> village = VillageManager.get(level).findNearestVillage(pos, Village.MERGE_MARGIN);
            if (village.isEmpty()) return Optional.empty();
            Building building = McaBuildingCompat.buildingAt(level, village.get(), pos);
            if (building == null) return Optional.empty();
            return Optional.ofNullable(building(ApiSupport.villageId(level, village.get()), village.get(), building));
        } catch (Throwable t) {
            ApiSupport.swallow("villages.buildingAt", t);
            return Optional.empty();
        }
    }

    @Override
    public Optional<VillageNeedsSummary> needs(MinecraftServer server, VillageId id) {
        try {
            if (server == null || id == null) return Optional.empty();
            return ResidentRegister.get(server).summary(server, id);
        } catch (Throwable t) {
            ApiSupport.swallow("villages.needs", t);
            return Optional.empty();
        }
    }

    @Override
    public List<String> spiritIds() {
        List<String> out = new ArrayList<>();
        for (SpiritRegistry.Spirit spirit : SpiritRegistry.ordered()) out.add(spirit.id());
        return out;
    }

    @Override
    public boolean isKnownSpirit(String spiritId) {
        return spiritId != null && SpiritRegistry.contains(spiritId);
    }

    @Override
    public List<Integer> spiritTierThresholds() {
        List<Integer> out = new ArrayList<>();
        for (int threshold : VillageSpiritAggregator.tierThresholds()) out.add(threshold);
        return out;
    }

    // ---- conversions, shared with the event posts ----

    static VillageSnapshot snapshot(ServerLevel level, Village village) {
        VillageId id = ApiSupport.villageId(level, village);
        Vec3i center = village.getCenter();
        BoundingBox box = village.getBox();
        List<UUID> residents = village.getResidentsUUIDs().toList();
        int loaded = 0;
        for (UUID uuid : residents) {
            if (level.getEntity(uuid) != null) loaded++;
        }
        long established = Long.MIN_VALUE;
        boolean playerFounded = false;
        WorldCalendarSavedData.VillageBirth birth = WorldCalendarSavedData.get(level.getServer())
                .getVillageBirth(new WorldCalendarSavedData.VillageKey(id.dimension(), id.villageId()));
        if (birth != null) {
            established = birth.worldDay();
            playerFounded = birth.playerFounded();
        }
        return new VillageSnapshot(id, village.getName(), new BlockPos(center),
                new BlockPos(box.minX(), box.minY(), box.minZ()), new BlockPos(box.maxX(), box.maxY(), box.maxZ()),
                residents.size(), loaded, residents, established, playerFounded, McaBuildings.all(village).size());
    }

    static SpiritSnapshot spirit(ServerLevel level, Village village) {
        VillageSpiritCache.Entry cached = VillageSpiritCache.get(level, village.getId());
        SpiritTotals totals;
        SpiritReadout readout;
        if (cached != null) {
            totals = cached.totals();
            readout = cached.readout();
        } else {
            totals = VillageSpiritAggregator.totalsFor(village);
            readout = VillageSpiritAggregator.readoutFor(totals);
        }
        return spirit(ApiSupport.villageId(level, village), totals, readout);
    }

    static SpiritSnapshot spirit(VillageId id, SpiritTotals totals, SpiritReadout readout) {
        List<Integer> thresholds = new ArrayList<>();
        for (int threshold : VillageSpiritAggregator.tierThresholds()) thresholds.add(threshold);
        return new SpiritSnapshot(id, totals.perSpirit(), totals.total(), totals.contributingBuildings(),
                readout.classification().name().toLowerCase(Locale.ROOT), readout.tierIndex(),
                Optional.ofNullable(readout.primarySpiritId()), Optional.ofNullable(readout.secondarySpiritId()),
                thresholds, readout.asComponent());
    }

    static @Nullable BuildingSnapshot building(VillageId id, Village village, Building building) {
        String type = McaBuildingCompat.effectiveType(village, building);
        if (type == null) type = building.getType();
        if (type == null) type = "";
        BlockPos p0 = building.getPos0();
        BlockPos p1 = building.getPos1();
        return new BuildingSnapshot(id, building.getId(), type, family(type), tier(type), building.getSize(),
                building.getCenter(),
                new BlockPos(Math.min(p0.getX(), p1.getX()), Math.min(p0.getY(), p1.getY()), Math.min(p0.getZ(), p1.getZ())),
                new BlockPos(Math.max(p0.getX(), p1.getX()), Math.max(p0.getY(), p1.getY()), Math.max(p0.getZ(), p1.getZ())),
                building.isComplete());
    }

    /** {@code dock_l3} to {@code dock}; anything without a tier suffix is its own family. */
    static String family(String type) {
        int at = suffixAt(type);
        return at < 0 ? type : type.substring(0, at);
    }

    static int tier(String type) {
        int at = suffixAt(type);
        if (at < 0) return 1;
        try {
            return Math.max(1, Integer.parseInt(type.substring(at + 2)));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private static int suffixAt(String type) {
        if (type == null) return -1;
        int at = type.lastIndexOf("_l");
        if (at < 0 || at + 2 >= type.length()) return -1;
        for (int i = at + 2; i < type.length(); i++) {
            if (!Character.isDigit(type.charAt(i))) return -1;
        }
        return at;
    }
}
