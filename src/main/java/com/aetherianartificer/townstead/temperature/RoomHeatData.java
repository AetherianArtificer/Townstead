package com.aetherianartificer.townstead.temperature;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** Per-dimension stored temperatures; topology is rebuilt from loaded blocks after a restart. */
public final class RoomHeatData extends SavedData {
    private final Map<Long, Double> temperatures = new LinkedHashMap<>();
    public static RoomHeatData get(ServerLevel level) {
        //? if >=1.21 {
        return level.getDataStorage().computeIfAbsent(new Factory<>(RoomHeatData::new, RoomHeatData::load), "townstead_room_heat");
        //?} else {
        /*return level.getDataStorage().computeIfAbsent(RoomHeatData::load, RoomHeatData::new, "townstead_room_heat");
        *///?}
    }
    public double temperature(long anchor, double fallback) { return temperatures.getOrDefault(anchor, fallback); }
    public void put(long anchor, double value) {
        if (!Double.isFinite(value)) return;
        temperatures.put(anchor, value);
        while (temperatures.size() > 4096) temperatures.remove(temperatures.keySet().iterator().next());
        setDirty();
    }
    //? if >=1.21 {
    public static RoomHeatData load(CompoundTag tag, HolderLookup.Provider provider) {
    //?} else {
    /*public static RoomHeatData load(CompoundTag tag) {
    *///?}
        RoomHeatData data = new RoomHeatData();
        int count = Math.max(0, Math.min(4096, tag.getInt("count")));
        for (int i = 0; i < count; i++) {
            float value = tag.getFloat("temperature_" + i);
            if (Float.isFinite(value)) data.temperatures.put(tag.getLong("anchor_" + i), (double) value);
        }
        return data;
    }
    //? if >=1.21 {
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
    //?} else {
    /*@Override public CompoundTag save(CompoundTag tag) {
    *///?}
        int index = 0;
        for (var entry : temperatures.entrySet()) {
            tag.putLong("anchor_" + index, entry.getKey());
            tag.putFloat("temperature_" + index, entry.getValue().floatValue());
            index++;
        }
        tag.putInt("count", index);
        return tag;
    }
}
