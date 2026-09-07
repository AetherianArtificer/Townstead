package com.aetherianartificer.townstead.temperature;
import com.aetherianartificer.townstead.compat.temperature.RoomHeatBackend;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RoomHeatBackendScaleTest {
    @Test void tanUsesNearestRepresentativeTemperatureWithoutCollapsingColdAndHot() {
        assertEquals(0,RoomHeatBackend.tanOrdinal(-10));
        assertEquals(1,RoomHeatBackend.tanOrdinal(5));
        assertEquals(2,RoomHeatBackend.tanOrdinal(20));
        assertEquals(3,RoomHeatBackend.tanOrdinal(30));
        assertEquals(4,RoomHeatBackend.tanOrdinal(40));
    }
    @Test void heatSettingsRejectInvalidCapacityAndCanBeDisabled() {
        var data=com.google.gson.JsonParser.parseString("{\"room_heat_enabled\":false,\"room_heat_capacity\":-1}").getAsJsonObject();
        var settings=TemperatureSettings.parse(data);
        assertFalse(settings.roomHeatEnabled());
        assertTrue(settings.roomHeatCapacity()>0);
    }
}
