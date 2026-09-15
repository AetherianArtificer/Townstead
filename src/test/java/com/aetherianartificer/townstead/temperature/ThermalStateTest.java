package com.aetherianartificer.townstead.temperature;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ThermalStateTest {
    @AfterEach void clear() { TemperatureClientStore.clear(); }

    @Test void wetnessAndComfortSurviveSave() {
        var original = new ThermalComfort.State(0.75f, -8, 22);
        var tag = new CompoundTag();
        original.write(tag);
        assertEquals(original, ThermalComfort.State.read(tag));
    }
    @Test void legacyWetSaveGetsPersistentWetness() {
        var tag = new CompoundTag();
        tag.putBoolean("wet", true);
        assertEquals(new ThermalComfort.State(1, 0, 0), ThermalComfort.State.read(tag));
    }
    @Test void coldComfortAndNormalCoreArriveIndependently() {
        int flags = TemperatureSyncPayload.flags(true, true, TemperatureData.Tier.COLD.ordinal())
                | (TemperatureData.Tier.COMFORTABLE.ordinal() << 5);
        TemperatureClientStore.set(1, 370, 120, flags);
        assertEquals(TemperatureData.Tier.COLD, TemperatureClientStore.getTier(1));
        assertEquals(TemperatureData.Tier.COMFORTABLE, TemperatureClientStore.getCoreTier(1));
        assertTrue(TemperatureClientStore.isWet(1));
        assertTrue(TemperatureClientStore.isSeekingRelief(1));
    }
    @Test void coreWarningCanPersistAfterComfortRecovers() {
        int flags = TemperatureSyncPayload.flags(false, true, TemperatureData.Tier.COMFORTABLE.ordinal())
                | (TemperatureData.Tier.FREEZING.ordinal() << 5);
        TemperatureClientStore.set(1, 330, 200, flags);
        assertEquals(TemperatureData.Tier.COMFORTABLE, TemperatureClientStore.getTier(1));
        assertEquals(TemperatureData.Tier.FREEZING, TemperatureClientStore.getCoreTier(1));
        assertFalse(TemperatureClientStore.isWet(1));
    }
}
