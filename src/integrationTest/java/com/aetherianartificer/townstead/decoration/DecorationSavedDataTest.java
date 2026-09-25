package com.aetherianartificer.townstead.decoration;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DecorationSavedDataTest {
    @Test void roundTripPreservesDefinitionsMembersAndSpatialIndex() {
        BlockPos anchor = new BlockPos(10, 64, 20);
        var instance = new DecorationInstance(ResourceLocation.tryParse("townstead:well"),
                anchor, List.of(anchor.above()));
        var data = new DecorationSavedData();
        data.put(instance);
        //? if >=1.21 {
        CompoundTag saved = data.save(new CompoundTag(), null);
        //?} else {
        /*CompoundTag saved = data.save(new CompoundTag());
        *///?}
        assertEquals("townstead:well", saved.getList("decorations", Tag.TAG_COMPOUND)
                .getCompound(0).getString("decoration"));

        //? if >=1.21 {
        var loaded = DecorationSavedData.load(saved, null);
        //?} else {
        /*var loaded = DecorationSavedData.load(saved);
        *///?}
        assertEquals(instance, loaded.at(anchor));
        assertEquals(List.of(instance), loaded.within(anchor, 6));
        assertTrue(loaded.within(new BlockPos(100, 64, 100), 6).isEmpty());
        //? if >=1.21 {
        assertEquals(saved, loaded.save(new CompoundTag(), null));
        //?} else {
        /*assertEquals(saved, loaded.save(new CompoundTag()));
        *///?}
    }
}
