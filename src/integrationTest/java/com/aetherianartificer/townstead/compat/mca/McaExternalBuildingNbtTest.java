package com.aetherianartificer.townstead.compat.mca;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;

class McaExternalBuildingNbtTest {
    //? if >=1.21 {
    @Test
    void syntheticPayloadConstructsAnMcaExternalBuilding() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("id", -42);
        tag.putString("type", "dock_l1");
        tag.put("blocks2", new CompoundTag());
        McaBuildingNbt.putDetachedDefaults(tag);

        net.conczin.mca.server.world.data.ExternalBuilding building = assertDoesNotThrow(
                () -> new net.conczin.mca.server.world.data.ExternalBuilding(tag));
        assertFalse(building.isFunctionalRoom());
    }

    //?}
}
