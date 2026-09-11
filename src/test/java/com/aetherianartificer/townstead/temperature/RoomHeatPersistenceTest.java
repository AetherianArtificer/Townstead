package com.aetherianartificer.townstead.temperature;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RoomHeatPersistenceTest {
    @Test void directionalStorageMigratesOnceToOnePhysicalWallAndSurvivesReload() {
        RoomHeatData data = new RoomHeatData();
        var pos = new net.minecraft.core.BlockPos(3,64,4);
        data.putWall(new ThermalRegionScan.Face(pos.offset(1,0,0).asLong(),pos.asLong()),"wood",30);
        data.putWall(new ThermalRegionScan.Face(pos.offset(-1,0,0).asLong(),pos.asLong()),"wood",10);
        assertEquals(20,data.solidTemperature(pos.asLong(),"wood",0));
        data.putSolid(pos.asLong(),"wood",21);
        assertEquals(21,data.solidTemperature(pos.asLong(),"wood",0));
        //? if >=1.21 {
        var restored=RoomHeatData.load(data.save(new CompoundTag(),null),null);
        //?} else {
        /*var restored=RoomHeatData.load(data.save(new CompoundTag()));
        *///?}
        assertEquals(21,restored.solidTemperature(pos.asLong(),"wood",0));
        restored.retainSolidMaterial(pos.asLong(),"wood");
        assertEquals(21,restored.solidTemperature(pos.asLong(),"wood",0));
        restored.retainSolidMaterial(pos.asLong(),"air");
        assertEquals(0,restored.solidTemperature(pos.asLong(),"wood",0));
        restored.putSolid(pos.asLong(),"wood",Double.NaN);
        assertEquals(0,restored.solidTemperature(pos.asLong(),"wood",0));
    }
    @Test void storedHeatSurvivesSaveAndLoad() {
        RoomHeatData original=new RoomHeatData();original.put(42L,27.5);
        var face = new ThermalRegionScan.Face(42, 43);
        original.putWall(face, "minecraft:stone/MASONRY", 31.25);
        //? if >=1.21 {
        RoomHeatData restored=RoomHeatData.load(original.save(new CompoundTag(),null),null);
        //?} else {
        /*RoomHeatData restored=RoomHeatData.load(original.save(new CompoundTag()));
        *///?}
        assertEquals(27.5,restored.temperature(42L,10));
        assertEquals(10,restored.temperature(99L,10));
        assertEquals(31.25, restored.wallTemperature(face, "minecraft:stone/MASONRY", 10));
        assertEquals(10, restored.wallTemperature(face, "minecraft:oak_planks/WOOD", 10));
        assertEquals(10, restored.wallTemperature(new ThermalRegionScan.Face(44, 43), "minecraft:stone/MASONRY", 10));
        restored.retainWallMaterial(face, "minecraft:stone/MASONRY");
        assertEquals(31.25, restored.wallTemperature(face, "minecraft:stone/MASONRY", 10));
        restored.retainWallMaterial(face, "minecraft:air/MASONRY");
        assertEquals(10, restored.wallTemperature(face, "minecraft:stone/MASONRY", 10));
    }
    @Test void invalidTemperaturesCannotPoisonTheSave() {
        RoomHeatData data=new RoomHeatData();data.put(1L,Double.NaN);
        assertEquals(10,data.temperature(1L,10));
        var face = new ThermalRegionScan.Face(1, 2);
        data.putWall(face, "stone", Double.NaN);
        assertEquals(10, data.wallTemperature(face, "stone", 10));
    }
}
