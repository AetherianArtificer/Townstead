package com.aetherianartificer.townstead.inventory;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.world.entity.EquipmentSlot;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class AssignedArmorTest {
    @Test void assignmentsSurviveSaveAndRemovingOneRestoresOnlyThatSlotToAutomatic() throws Exception {
        var state = new CompoundTag();
        AssignedArmor.set(state, EquipmentSlot.HEAD, true);
        AssignedArmor.set(state, EquipmentSlot.CHEST, true);
        var saved = new ByteArrayOutputStream();
        NbtIo.writeCompressed(state, saved);
        var loaded = NbtIo.readCompressed(new ByteArrayInputStream(saved.toByteArray()), NbtAccounter.unlimitedHeap());
        assertTrue(AssignedArmor.protects(loaded, EquipmentSlot.HEAD, true));
        assertTrue(AssignedArmor.protects(loaded, EquipmentSlot.CHEST, true));
        assertFalse(AssignedArmor.protects(loaded, EquipmentSlot.LEGS, true));
        AssignedArmor.set(loaded, EquipmentSlot.HEAD, false);
        assertFalse(AssignedArmor.protects(loaded, EquipmentSlot.HEAD, true));
        assertTrue(AssignedArmor.protects(loaded, EquipmentSlot.CHEST, true));
    }

    @Test void brokenHelmetDoesNotAccidentallyPinItsAutomaticReplacement() {
        var state = new CompoundTag();
        AssignedArmor.set(state, EquipmentSlot.HEAD, true);
        assertFalse(AssignedArmor.protects(state, EquipmentSlot.HEAD, false));
        assertFalse(AssignedArmor.protects(state, EquipmentSlot.HEAD, true));
    }

    @Test void allArmorSlotsAreIndependentAndNeverTakeOwnershipOfCombatHands() {
        var state = new CompoundTag();
        for (var slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET})
            AssignedArmor.set(state, slot, true);
        AssignedArmor.set(state, EquipmentSlot.MAINHAND, true);
        AssignedArmor.set(state, EquipmentSlot.OFFHAND, true);
        assertFalse(AssignedArmor.protects(state, EquipmentSlot.MAINHAND, true));
        assertFalse(AssignedArmor.protects(state, EquipmentSlot.OFFHAND, true));
        AssignedArmor.set(state, EquipmentSlot.FEET, false);
        for (var slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS})
            assertTrue(AssignedArmor.protects(state, slot, true));
        assertFalse(AssignedArmor.protects(state, EquipmentSlot.FEET, true));
    }
}
