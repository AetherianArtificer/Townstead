package com.aetherianartificer.townstead.temperature;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RoomHeatRecoveryTest {
    @Test void impossibleLegacyHeatRebuildsTheWholeContaminatedCache() {
        CompoundTag tag = fixture();
        tag.putFloat("temperature_0", -1977.1399f); // MoreTest cave at 735,49,159
        tag.putFloat("solid_0_temperature", -1931.288f);
        //? if >=1.21 {
        var data = RoomHeatData.load(tag, null);
        //?} else {
        /*var data = RoomHeatData.load(tag);
        *///?}
        assertEquals(0, data.temperature(1, 0));
        assertEquals(0, data.temperature(2, 0));
        assertEquals(0, data.solidTemperature(3, "minecraft:stone/MASONRY", 0));
        assertTrue(data.isDirty());
        //? if >=1.21 {
        var saved = data.save(new CompoundTag(), null);
        //?} else {
        /*var saved = data.save(new CompoundTag());
        *///?}
        assertEquals(0, saved.getInt("count"));
        assertEquals(0, saved.getInt("solid_count"));
    }

    @Test void legitimateStoredWarmthSurvivesSaveAndReload() {
        //? if >=1.21 {
        var data = RoomHeatData.load(fixture(), null);
        var restored = RoomHeatData.load(data.save(new CompoundTag(), null), null);
        //?} else {
        /*var data = RoomHeatData.load(fixture());
        var restored = RoomHeatData.load(data.save(new CompoundTag()));
        *///?}
        assertEquals(22, restored.temperature(1, 0));
        assertEquals(18, restored.temperature(2, 0));
        assertEquals(19, restored.solidTemperature(3, "minecraft:stone/MASONRY", 0));
    }

    private static CompoundTag fixture() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("count", 2);
        tag.putLong("anchor_0", 1); tag.putFloat("temperature_0", 22);
        tag.putLong("anchor_1", 2); tag.putFloat("temperature_1", 18);
        tag.putInt("solid_count", 1); tag.putLong("solid_0_pos", 3);
        tag.putString("solid_0_material", "minecraft:stone/MASONRY");
        tag.putFloat("solid_0_temperature", 19);
        return tag;
    }
}
