package com.aetherianartificer.townstead.village;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.compat.mca.McaBuildingNbt;
import com.aetherianartificer.townstead.compat.mca.McaBuildings;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Gives open-air building geometry back to MCA.
 *
 * <p>Earlier versions moved a landing's or pen's block positions out of MCA's building NBT and
 * into a Townstead overlay, because dock recipes named generic material and the resulting NBT
 * could break the client sync. The recipes name distinctive blocks now, so the overlay bought
 * nothing and cost a second, disagreeing answer to "how big is this building". This pass writes
 * the stored positions back into MCA's own record and releases the overlays.</p>
 *
 * <p>MCA's copy was deliberately truncated while the overlay held the full set, so the overlay is
 * the side that knows. A building whose overlay carried no positions keeps whatever MCA already
 * has; the next time a player adds it, recognition rewrites it in full.</p>
 */
public final class TownsteadVillageMigration {
    private TownsteadVillageMigration() {}

    public static Result migrateServer(MinecraftServer server) {
        if (server == null) return new Result(0, 0);
        TownsteadVillageSavedData data = TownsteadVillageSavedData.get(server);
        int villages = 0;
        int buildings = 0;
        for (ServerLevel level : server.getAllLevels()) {
            for (Village village : VillageManager.get(level)) {
                villages++;
                buildings += migrateVillage(level, village);
            }
        }
        data.markSchemaMigrationComplete();
        return new Result(villages, buildings);
    }

    public static Result migrateServerIfNeeded(MinecraftServer server) {
        if (server == null) return new Result(0, 0);
        TownsteadVillageSavedData data = TownsteadVillageSavedData.get(server);
        if (!data.needsAutomaticMigration()) return new Result(0, 0);
        return migrateServer(server);
    }

    public static int migrateVillage(ServerLevel level, Village village) {
        if (level == null || village == null) return 0;
        TownsteadVillageSavedData data = TownsteadVillageSavedData.get(level.getServer());
        TownsteadVillageSavedData.VillageRecord record = data.getRecord(level, village.getId());
        if (record == null || record.buildings().isEmpty()) return 0;

        List<Map.Entry<Integer, CompoundTag>> restored = new ArrayList<>();
        record.buildings().forEach((buildingId, overlay) -> {
            Building building = McaBuildings.byId(village, buildingId);
            if (building == null || overlay.blockPositions().isEmpty()) return;
            restored.add(Map.entry(buildingId, nbtWithFullGeometry(buildingId, building, overlay)));
        });
        for (Map.Entry<Integer, CompoundTag> entry : restored) {
            McaBuildings.putSynthetic(village, entry.getKey(), entry.getValue());
        }
        data.clearBuildings(level, village.getId());
        if (!restored.isEmpty()) {
            village.calculateDimensions();
            village.markDirty();
            Townstead.LOGGER.info("Returned geometry for {} open-air buildings in village {} to MCA",
                    restored.size(), village.getId());
        }
        return restored.size();
    }

    public record Result(int villagesScanned, int buildingsMigrated) {}

    private static CompoundTag nbtWithFullGeometry(
            int id, Building building, TownsteadVillageSavedData.BuildingOverlay overlay) {
        int[] bounds = overlay.bounds();
        BlockPos min = bounds.length == 6 ? new BlockPos(bounds[0], bounds[1], bounds[2]) : building.getPos0();
        BlockPos max = bounds.length == 6 ? new BlockPos(bounds[3], bounds[4], bounds[5]) : building.getPos1();
        BlockPos center = building.getCenter();

        CompoundTag tag = new CompoundTag();
        tag.putInt("id", id);
        tag.putInt("size", overlay.totalPositions());
        tag.putInt("pos0X", min.getX());
        tag.putInt("pos0Y", min.getY());
        tag.putInt("pos0Z", min.getZ());
        tag.putInt("pos1X", max.getX());
        tag.putInt("pos1Y", max.getY());
        tag.putInt("pos1Z", max.getZ());
        tag.putInt("posX", center.getX());
        tag.putInt("posY", center.getY());
        tag.putInt("posZ", center.getZ());
        tag.putBoolean("isTypeForced", true);
        tag.putBoolean("strictScan", false);
        tag.putString("type", building.getType());
        McaBuildingNbt.putDetachedDefaults(tag);

        CompoundTag blocks = new CompoundTag();
        overlay.blockPositions().forEach((blockId, packed) -> {
            ListTag list = new ListTag();
            for (long position : packed) list.add(McaBuildingNbt.blockPos(BlockPos.of(position)));
            blocks.put(blockId, list);
        });
        tag.put("blocks2", blocks);
        return tag;
    }
}
