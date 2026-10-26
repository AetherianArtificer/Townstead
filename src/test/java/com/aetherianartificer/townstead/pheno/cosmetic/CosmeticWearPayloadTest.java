package com.aetherianartificer.townstead.pheno.cosmetic;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CosmeticWearPayloadTest {

    @Test
    void syncKeepsSlotItemAndExpiry() {
        var worn = new CosmeticWear.Worn(EquipmentSlot.HEAD, ResourceLocation.tryParse("minecraft:zombie_head"), 12_345L);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        new CosmeticWearS2CPayload(42, List.of(worn)).write(buffer);
        CosmeticWearS2CPayload read = CosmeticWearS2CPayload.read(buffer);

        assertEquals(42, read.entityId());
        assertEquals(List.of(worn), read.worn());
    }

    @Test
    void emptyListMeansNothingIsWorn() {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        new CosmeticWearS2CPayload(7, List.of()).write(buffer);
        assertTrue(CosmeticWearS2CPayload.read(buffer).worn().isEmpty(), "an empty sync clears the entity's cosmetics");
    }

    @Test
    void slotNamesMatchEquipmentSlots() {
        assertEquals(EquipmentSlot.HEAD, CosmeticWear.slotByName("head"));
        assertEquals(EquipmentSlot.MAINHAND, CosmeticWear.slotByName("mainhand"));
        assertNull(CosmeticWear.slotByName("hat"));
    }
}
