package com.aetherianartificer.townstead.temperature;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RoomHeatPersistenceTest {
    @Test void storedHeatSurvivesSaveAndLoad() {
        RoomHeatData original=new RoomHeatData();original.put(42L,27.5);
        //? if >=1.21 {
        RoomHeatData restored=RoomHeatData.load(original.save(new CompoundTag(),null),null);
        //?} else {
        /*RoomHeatData restored=RoomHeatData.load(original.save(new CompoundTag()));
        *///?}
        assertEquals(27.5,restored.temperature(42L,10));
        assertEquals(10,restored.temperature(99L,10));
    }
    @Test void invalidTemperaturesCannotPoisonTheSave() {
        RoomHeatData data=new RoomHeatData();data.put(1L,Double.NaN);
        assertEquals(10,data.temperature(1L,10));
    }
}
