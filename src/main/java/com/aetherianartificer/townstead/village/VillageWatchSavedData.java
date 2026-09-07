package com.aetherianartificer.townstead.village;

import com.aetherianartificer.townstead.api.v1.model.VillageId;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;
//? if >=1.21 {
import net.minecraft.core.HolderLookup;
//?}

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What Townstead last observed about each village, persisted so the building, spirit and need
 * watchers can tell a real change from a first sighting after a restart. Without this, every
 * watcher re-seeds silently on server start and a change that happened across the restart is
 * never reported.
 */
public final class VillageWatchSavedData extends SavedData {
    public static final String FILE_ID = "townstead_village_watch";

    private final Map<String, Watch> watches = new HashMap<>();

    public VillageWatchSavedData() {}

    public static VillageWatchSavedData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        //? if >=1.21 {
        return overworld.getDataStorage().computeIfAbsent(
                new Factory<>(VillageWatchSavedData::new, VillageWatchSavedData::load), FILE_ID);
        //?} else {
        /*return overworld.getDataStorage().computeIfAbsent(
                VillageWatchSavedData::load, VillageWatchSavedData::new, FILE_ID);
        *///?}
    }

    public static String keyOf(ServerLevel level, int villageId) {
        return level.dimension().location() + "|" + villageId;
    }

    public static String keyOf(VillageId id) {
        return id.dimension() + "|" + id.villageId();
    }

    public @Nullable Map<Integer, String> buildingTypes(String key) {
        Watch watch = watches.get(key);
        return watch == null || watch.buildingTypes == null ? null : Map.copyOf(watch.buildingTypes);
    }

    public void putBuildingTypes(String key, Map<Integer, String> types) {
        watch(key).buildingTypes = new HashMap<>(types);
        setDirty();
    }

    public @Nullable SpiritMark spirit(String key) {
        Watch watch = watches.get(key);
        return watch == null ? null : watch.spirit;
    }

    public void putSpirit(String key, SpiritMark mark) {
        watch(key).spirit = mark;
        setDirty();
    }

    public Map<String, String> needBands(String key) {
        Watch watch = watches.get(key);
        return watch == null ? Map.of() : Map.copyOf(watch.needBands);
    }

    public void putNeedBands(String key, Map<String, String> bands) {
        watch(key).needBands = new LinkedHashMap<>(bands);
        setDirty();
    }

    private Watch watch(String key) {
        return watches.computeIfAbsent(key, k -> new Watch());
    }

    /** The last spirit readout shape observed. */
    public record SpiritMark(String classification, int tierIndex, @Nullable String primary, @Nullable String secondary) {
    }

    private static final class Watch {
        @Nullable Map<Integer, String> buildingTypes;
        @Nullable SpiritMark spirit;
        Map<String, String> needBands = new LinkedHashMap<>();
    }

    //? if >=1.21 {
    public static VillageWatchSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
    //?} else {
    /*public static VillageWatchSavedData load(CompoundTag tag) {
    *///?}
        VillageWatchSavedData data = new VillageWatchSavedData();
        ListTag list = tag.getList("villages", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            Watch watch = new Watch();
            if (t.contains("buildings", Tag.TAG_COMPOUND)) {
                CompoundTag buildings = t.getCompound("buildings");
                watch.buildingTypes = new HashMap<>();
                for (String id : buildings.getAllKeys()) {
                    try {
                        watch.buildingTypes.put(Integer.parseInt(id), buildings.getString(id));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            if (t.contains("spirit", Tag.TAG_COMPOUND)) {
                CompoundTag spirit = t.getCompound("spirit");
                watch.spirit = new SpiritMark(spirit.getString("classification"), spirit.getInt("tier"),
                        spirit.contains("primary") ? spirit.getString("primary") : null,
                        spirit.contains("secondary") ? spirit.getString("secondary") : null);
            }
            if (t.contains("bands", Tag.TAG_COMPOUND)) {
                CompoundTag bands = t.getCompound("bands");
                for (String need : bands.getAllKeys()) watch.needBands.put(need, bands.getString(need));
            }
            data.watches.put(t.getString("key"), watch);
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
        ListTag list = new ListTag();
        for (Map.Entry<String, Watch> e : watches.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putString("key", e.getKey());
            Watch watch = e.getValue();
            if (watch.buildingTypes != null) {
                CompoundTag buildings = new CompoundTag();
                for (Map.Entry<Integer, String> b : watch.buildingTypes.entrySet()) {
                    if (b.getValue() != null) buildings.putString(Integer.toString(b.getKey()), b.getValue());
                }
                t.put("buildings", buildings);
            }
            if (watch.spirit != null) {
                CompoundTag spirit = new CompoundTag();
                spirit.putString("classification", watch.spirit.classification());
                spirit.putInt("tier", watch.spirit.tierIndex());
                if (watch.spirit.primary() != null) spirit.putString("primary", watch.spirit.primary());
                if (watch.spirit.secondary() != null) spirit.putString("secondary", watch.spirit.secondary());
                t.put("spirit", spirit);
            }
            if (!watch.needBands.isEmpty()) {
                CompoundTag bands = new CompoundTag();
                for (Map.Entry<String, String> b : watch.needBands.entrySet()) bands.putString(b.getKey(), b.getValue());
                t.put("bands", bands);
            }
            list.add(t);
        }
        tag.put("villages", list);
        return tag;
    }

    @SuppressWarnings("unused")
    private static ResourceLocation unused() {
        return null;
    }
}
