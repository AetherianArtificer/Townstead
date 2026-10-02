package com.aetherianartificer.townstead.village;

import com.aetherianartificer.townstead.Townstead;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Per-overworld persistence for Townstead's village overlay data.
 *
 * <p>MCA is the source of truth for villages and buildings, geometry included. This data holds
 * only what MCA has nowhere to put.</p>
 *
 * <p>It used to carry a second copy of every open-air building's footprint, to keep MCA's own NBT
 * small when dock recipes promoted planks and stone to trackable blocks. Those recipes name
 * distinctive blocks now, so there is nothing heavy left to move out, and one geometry is better
 * than two that can disagree. {@link TownsteadVillageMigration} hands the stored positions back
 * and drops the overlays; the loader below still reads them so that it can.</p>
 */
public class TownsteadVillageSavedData extends SavedData {
    public static final String FILE_ID = "townstead_villages";
    /** v2 gave the geometry back to MCA, so a village record no longer carries overlays. */
    public static final int SCHEMA_VERSION = 2;

    private static final String KEY_SCHEMA_VERSION = "schemaVersion";
    private static final String KEY_RECORDS = "villages";
    private static final String KEY_DIM = "dim";
    private static final String KEY_ID = "id";
    private static final String KEY_REVISION = "revision";
    private static final String KEY_LAST_SEEN = "lastSeen";
    private static final String KEY_BUILDINGS = "buildings";
    private static final String KEY_BUILDING_ID = "buildingId";
    private static final String KEY_KIND = "kind";
    private static final String KEY_TYPE = "type";
    private static final String KEY_BOUNDS = "bounds";
    private static final String KEY_BLOCK_KEYS = "blockKeys";
    private static final String KEY_BLOCK_POSITIONS = "blockPositions";

    private final Map<VillageKey, VillageRecord> records = new HashMap<>();
    private int loadedSchemaVersion = SCHEMA_VERSION;
    private boolean schemaMigrationComplete;

    public record VillageKey(ResourceLocation dimension, int villageId) {
        public static VillageKey of(ServerLevel level, int villageId) {
            return new VillageKey(level.dimension().location(), villageId);
        }
    }

    public static final class VillageRecord {
        private int revision;
        private long lastSeenGameTime;
        private final Int2ObjectOpenHashMap<BuildingOverlay> buildings = new Int2ObjectOpenHashMap<>();

        private VillageRecord(int revision, long lastSeenGameTime) {
            this.revision = revision;
            this.lastSeenGameTime = lastSeenGameTime;
        }

        public int revision() {
            return revision;
        }

        public long lastSeenGameTime() {
            return lastSeenGameTime;
        }

        public Int2ObjectMap<BuildingOverlay> buildings() {
            return buildings;
        }

        public int totalTrackedPositions() {
            int total = 0;
            for (BuildingOverlay overlay : buildings.values()) total += overlay.totalPositions();
            return total;
        }
    }

    public record BuildingOverlay(String kind, String type, int[] bounds, Map<String, long[]> blockPositions) {
        public BuildingOverlay {
            bounds = bounds == null ? new int[0] : Arrays.copyOf(bounds, bounds.length);
            Map<String, long[]> copy = new HashMap<>();
            if (blockPositions != null) {
                for (Map.Entry<String, long[]> entry : blockPositions.entrySet()) {
                    copy.put(entry.getKey(), entry.getValue() == null ? new long[0] : Arrays.copyOf(entry.getValue(), entry.getValue().length));
                }
            }
            blockPositions = Map.copyOf(copy);
        }

        public int totalPositions() {
            int total = 0;
            for (long[] positions : blockPositions.values()) total += positions.length;
            return total;
        }
    }

    public TownsteadVillageSavedData() {}

    public static TownsteadVillageSavedData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        //? if >=1.21 {
        return overworld.getDataStorage().computeIfAbsent(
                new Factory<>(TownsteadVillageSavedData::new, TownsteadVillageSavedData::load),
                FILE_ID);
        //?} else {
        /*return overworld.getDataStorage().computeIfAbsent(
                TownsteadVillageSavedData::load,
                TownsteadVillageSavedData::new,
                FILE_ID);
        *///?}
    }

    //? if >=1.21 {
    public static TownsteadVillageSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
    //?} else {
    /*public static TownsteadVillageSavedData load(CompoundTag tag) {
    *///?}
        TownsteadVillageSavedData data = new TownsteadVillageSavedData();
        data.loadedSchemaVersion = tag.contains(KEY_SCHEMA_VERSION) ? tag.getInt(KEY_SCHEMA_VERSION) : 0;
        data.schemaMigrationComplete = tag.getBoolean("schemaMigrationComplete");
        if (!tag.contains(KEY_RECORDS, Tag.TAG_LIST)) return data;
        ListTag villages = tag.getList(KEY_RECORDS, Tag.TAG_COMPOUND);
        for (int i = 0; i < villages.size(); i++) {
            CompoundTag entry = villages.getCompound(i);
            ResourceLocation dim;
            try {
                dim = parseRl(entry.getString(KEY_DIM));
            } catch (Exception ignored) {
                continue;
            }
            VillageKey key = new VillageKey(dim, entry.getInt(KEY_ID));
            VillageRecord record = new VillageRecord(entry.getInt(KEY_REVISION), entry.getLong(KEY_LAST_SEEN));
            if (entry.contains(KEY_BUILDINGS, Tag.TAG_LIST)) {
                ListTag buildings = entry.getList(KEY_BUILDINGS, Tag.TAG_COMPOUND);
                for (int b = 0; b < buildings.size(); b++) {
                    CompoundTag buildingTag = buildings.getCompound(b);
                    BuildingOverlay overlay = loadOverlay(buildingTag);
                    record.buildings.put(buildingTag.getInt(KEY_BUILDING_ID), overlay);
                }
            }
            data.records.put(key, record);
        }
        return data;
    }

    //? if >=1.21 {
    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
    //?} else {
    /*@Override
    public CompoundTag save(CompoundTag tag) {
    *///?}
        ListTag villages = new ListTag();
        tag.putInt(KEY_SCHEMA_VERSION, SCHEMA_VERSION);
        tag.putBoolean("schemaMigrationComplete", schemaMigrationComplete);
        for (Map.Entry<VillageKey, VillageRecord> entry : records.entrySet()) {
            CompoundTag villageTag = new CompoundTag();
            villageTag.putString(KEY_DIM, entry.getKey().dimension().toString());
            villageTag.putInt(KEY_ID, entry.getKey().villageId());
            villageTag.putInt(KEY_REVISION, entry.getValue().revision);
            villageTag.putLong(KEY_LAST_SEEN, entry.getValue().lastSeenGameTime);
            villages.add(villageTag);
        }
        tag.put(KEY_RECORDS, villages);
        return tag;
    }

    public void touch(ServerLevel level, int villageId) {
        VillageRecord record = recordFor(VillageKey.of(level, villageId));
        record.lastSeenGameTime = level.getGameTime();
        setDirty();
    }

    /** Drops a village's legacy overlays once their geometry is back with MCA. */
    public void clearBuildings(ServerLevel level, int villageId) {
        VillageRecord record = records.get(VillageKey.of(level, villageId));
        if (record == null || record.buildings.isEmpty()) return;
        Townstead.LOGGER.debug("Released {} legacy overlays for village {}", record.buildings.size(), villageId);
        record.buildings.clear();
        record.revision++;
        record.lastSeenGameTime = level.getGameTime();
        setDirty();
    }

    public @Nullable VillageRecord getRecord(ServerLevel level, int villageId) {
        return records.get(VillageKey.of(level, villageId));
    }

    public int recordCount() {
        return records.size();
    }

    public int loadedSchemaVersion() {
        return loadedSchemaVersion;
    }

    public boolean needsAutomaticMigration() {
        return loadedSchemaVersion < SCHEMA_VERSION || !schemaMigrationComplete;
    }

    public boolean schemaMigrationComplete() {
        return schemaMigrationComplete;
    }

    public void markSchemaMigrationComplete() {
        if (!schemaMigrationComplete || loadedSchemaVersion != SCHEMA_VERSION) {
            schemaMigrationComplete = true;
            loadedSchemaVersion = SCHEMA_VERSION;
            setDirty();
        }
    }

    private VillageRecord recordFor(VillageKey key) {
        return records.computeIfAbsent(key, ignored -> new VillageRecord(0, 0L));
    }

    private static BuildingOverlay loadOverlay(CompoundTag tag) {
        String kind = tag.getString(KEY_KIND);
        String type = tag.getString(KEY_TYPE);
        int[] bounds = tag.getIntArray(KEY_BOUNDS);
        Map<String, long[]> positions = new HashMap<>();
        if (tag.contains(KEY_BLOCK_KEYS, Tag.TAG_LIST) && tag.contains(KEY_BLOCK_POSITIONS, Tag.TAG_LIST)) {
            ListTag keys = tag.getList(KEY_BLOCK_KEYS, Tag.TAG_STRING);
            ListTag values = tag.getList(KEY_BLOCK_POSITIONS, Tag.TAG_LONG_ARRAY);
            int count = Math.min(keys.size(), values.size());
            for (int i = 0; i < count; i++) {
                positions.put(keys.getString(i), ((LongArrayTag) values.get(i)).getAsLongArray());
            }
        }
        return new BuildingOverlay(kind, type, bounds, positions);
    }

    private static ResourceLocation parseRl(String value) {
        //? if >=1.21 {
        return ResourceLocation.parse(value);
        //?} else {
        /*return new ResourceLocation(value);
        *///?}
    }
}
