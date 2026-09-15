package com.aetherianartificer.townstead.temperature;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** Per-dimension stored temperatures; topology is rebuilt from loaded blocks after a restart. */
public final class RoomHeatData extends SavedData {
    private final Map<Long, Double> temperatures = new LinkedHashMap<>();
    private record Wall(String material, double temperature) {}
    private final Map<ThermalRegionScan.Face, Wall> walls = new LinkedHashMap<>(256, 0.75f, true);
    private static final int MAX_WALLS = 65536;
    private static final int[][] NEIGHBORS = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
    private final Map<Long, Wall> solids = new LinkedHashMap<>(256, 0.75f, true);
    public double solidTemperature(long pos, String material, double fallback) {
        Wall value = solids.get(pos);
        if (value != null) return value.material.equals(material) ? value.temperature : fallback;
        // Migrate directional storage once; preserve the mean of the observed old surfaces.
        double total = 0; int count = 0;
        for (var direction : NEIGHBORS) {
            var face = new ThermalRegionScan.Face(net.minecraft.core.BlockPos.of(pos).offset(direction[0],direction[1],direction[2]).asLong(), pos);
            Wall old = walls.get(face);
            if (old != null && old.material.equals(material)) { total += old.temperature; count++; }
        }
        return count == 0 ? fallback : total / count;
    }
    public void putSolid(long pos, String material, double temperature) {
        if (!Double.isFinite(temperature)) return;
        solids.put(pos, new Wall(material, temperature));
        for (var direction : NEIGHBORS)
            walls.remove(new ThermalRegionScan.Face(net.minecraft.core.BlockPos.of(pos).offset(direction[0],direction[1],direction[2]).asLong(), pos));
        while (solids.size() > MAX_WALLS) solids.remove(solids.keySet().iterator().next());
        setDirty();
    }
    public void retainSolidMaterial(long pos, String material) {
        Wall wall = solids.get(pos);
        if (wall != null && !wall.material.equals(material)) { solids.remove(pos); setDirty(); }
    }
    public double wallTemperature(ThermalRegionScan.Face face, String material, double fallback) {
        Wall wall = walls.get(face);
        return wall != null && wall.material.equals(material) ? wall.temperature : fallback;
    }
    public void putWall(ThermalRegionScan.Face face, String material, double temperature) {
        if (!Double.isFinite(temperature)) return;
        walls.put(face, new Wall(material, temperature));
        while (walls.size() > MAX_WALLS) walls.remove(walls.keySet().iterator().next());
        setDirty();
    }
    public void retainWallMaterial(ThermalRegionScan.Face face, String material) {
        Wall wall = walls.get(face);
        if (wall != null && !wall.material.equals(material)) { walls.remove(face); setDirty(); }
    }
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
        for (int i = 0; i < Math.max(0, Math.min(MAX_WALLS, tag.getInt("wall_count"))); i++) {
            String key = "wall_" + i + "_";
            double temperature = tag.getFloat(key + "temperature");
            if (tag.contains(key + "temperature") && Double.isFinite(temperature))
                data.walls.put(new ThermalRegionScan.Face(tag.getLong(key + "inside"), tag.getLong(key + "outside")),
                        new Wall(tag.getString(key + "material"), temperature));
        }
        for (int i = 0; i < Math.max(0, Math.min(MAX_WALLS, tag.getInt("solid_count"))); i++) {
            String key = "solid_" + i + "_";
            double temperature = tag.getFloat(key + "temperature");
            if (tag.contains(key + "temperature") && Double.isFinite(temperature))
                data.solids.put(tag.getLong(key + "pos"), new Wall(tag.getString(key + "material"), temperature));
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
        index = 0;
        for (var entry : walls.entrySet()) {
            String key = "wall_" + index++ + "_";
            tag.putLong(key + "inside", entry.getKey().inside());
            tag.putLong(key + "outside", entry.getKey().outside());
            tag.putString(key + "material", entry.getValue().material);
            tag.putFloat(key + "temperature", (float) entry.getValue().temperature);
        }
        tag.putInt("wall_count", index);
        index = 0;
        for (var entry : solids.entrySet()) {
            String key = "solid_" + index++ + "_";
            tag.putLong(key + "pos", entry.getKey());
            tag.putString(key + "material", entry.getValue().material);
            tag.putFloat(key + "temperature", (float) entry.getValue().temperature);
        }
        tag.putInt("solid_count", index);
        return tag;
    }
}
