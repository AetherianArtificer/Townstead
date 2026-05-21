package com.aetherianartificer.townstead.village;

import com.aetherianartificer.townstead.enclosure.EnclosureTypeIndex;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Eagerly moves Townstead-owned heavy synthetic-building details out of MCA's
 * building NBT and into Townstead's per-village SavedData overlay.
 */
public final class TownsteadVillageMigration {
    private static final int MCA_SENTINEL_POSITIONS_PER_BLOCK = 8;

    private TownsteadVillageMigration() {}

    public static Result migrateServer(MinecraftServer server) {
        if (server == null) return new Result(0, 0);
        TownsteadVillageSavedData data = TownsteadVillageSavedData.get(server);
        int villages = 0;
        int buildings = 0;
        for (ServerLevel level : server.getAllLevels()) {
            VillageManager manager = VillageManager.get(level);
            for (Village village : manager) {
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
        data.touch(level, village.getId());
        List<Map.Entry<Integer, Building>> replacements = new ArrayList<>();
        int migrated = 0;

        for (Map.Entry<Integer, Building> entry : village.getBuildings().entrySet()) {
            Building building = entry.getValue();
            String type = building.getType();
            String kind = kindOf(type);
            if (kind == null) continue;

            Map<String, List<BlockPos>> blocks = currentBlocks(level, building);
            BlockPos p0 = building.getPos0();
            BlockPos p1 = building.getPos1();
            data.putBuilding(
                    level,
                    village.getId(),
                    entry.getKey(),
                    new TownsteadVillageSavedData.BuildingOverlay(
                            kind,
                            type,
                            new int[] {p0.getX(), p0.getY(), p0.getZ(), p1.getX(), p1.getY(), p1.getZ()},
                            toPackedPositions(blocks)));
            replacements.add(Map.entry(entry.getKey(), new Building(compactBuildingNbt(entry.getKey(), building, blocks))));
            migrated++;
        }

        for (Map.Entry<Integer, Building> replacement : replacements) {
            village.getBuildings().put(replacement.getKey(), replacement.getValue());
        }
        if (migrated > 0) {
            village.markDirty();
        }
        return migrated;
    }

    public record Result(int villagesScanned, int buildingsMigrated) {}

    private static String kindOf(String type) {
        if (type == null) return null;
        if (type.startsWith("dock_")) return "dock";
        if (EnclosureTypeIndex.isEnclosureType(type)) return "enclosure";
        return null;
    }

    private static Map<String, List<BlockPos>> currentBlocks(ServerLevel level, Building building) {
        Map<String, List<BlockPos>> byId = new HashMap<>();
        for (BlockPos pos : (Iterable<BlockPos>) building.getBlockPosStream()::iterator) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) continue;
            String key = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
            byId.computeIfAbsent(key, ignored -> new ArrayList<>()).add(pos.immutable());
        }
        return byId;
    }

    private static CompoundTag compactBuildingNbt(int id, Building building, Map<String, List<BlockPos>> blocks) {
        BlockPos p0 = building.getPos0();
        BlockPos p1 = building.getPos1();
        BlockPos center = building.getCenter();
        CompoundTag tag = new CompoundTag();
        tag.putInt("id", id);
        tag.putInt("size", building.getSize());
        tag.putInt("pos0X", p0.getX());
        tag.putInt("pos0Y", p0.getY());
        tag.putInt("pos0Z", p0.getZ());
        tag.putInt("pos1X", p1.getX());
        tag.putInt("pos1Y", p1.getY());
        tag.putInt("pos1Z", p1.getZ());
        tag.putInt("posX", center.getX());
        tag.putInt("posY", center.getY());
        tag.putInt("posZ", center.getZ());
        tag.putBoolean("isTypeForced", true);
        tag.putString("type", building.getType());
        tag.putBoolean("strictScan", false);
        tag.put("blocks2", compactBlocksNbt(blocks));
        return tag;
    }

    private static CompoundTag compactBlocksNbt(Map<String, List<BlockPos>> blocks) {
        CompoundTag blocks2 = new CompoundTag();
        for (Map.Entry<String, List<BlockPos>> entry : blocks.entrySet()) {
            ListTag list = new ListTag();
            int emitted = 0;
            for (BlockPos pos : entry.getValue()) {
                if (emitted++ >= MCA_SENTINEL_POSITIONS_PER_BLOCK) break;
                CompoundTag posTag = new CompoundTag();
                posTag.putInt("x", pos.getX());
                posTag.putInt("y", pos.getY());
                posTag.putInt("z", pos.getZ());
                list.add(posTag);
            }
            blocks2.put(entry.getKey(), list);
        }
        return blocks2;
    }

    private static Map<String, long[]> toPackedPositions(Map<String, List<BlockPos>> blocks) {
        Map<String, long[]> packed = new HashMap<>();
        for (Map.Entry<String, List<BlockPos>> entry : blocks.entrySet()) {
            long[] values = new long[entry.getValue().size()];
            for (int i = 0; i < entry.getValue().size(); i++) values[i] = entry.getValue().get(i).asLong();
            packed.put(entry.getKey(), values);
        }
        return packed;
    }
}
