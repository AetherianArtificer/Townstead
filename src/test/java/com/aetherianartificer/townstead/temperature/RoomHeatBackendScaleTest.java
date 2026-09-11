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
    @Test void legacyTuningIsConvertedAndExplicitUnitsTakePrecedence() {
        var old = TemperatureSettings.parse(com.google.gson.JsonParser.parseString(
                "{\"room_heat_capacity\":2,\"room_source_power\":1.5,\"room_wall_conductance\":0.07}").getAsJsonObject());
        assertEquals(2000, old.roomHeatCapacity());
        assertEquals(250, old.roomSourcePower(), 0.001);
        assertEquals(1.4, old.roomWallConductance(), 0.001);
        var explicit = TemperatureSettings.parse(com.google.gson.JsonParser.parseString(
                "{\"thermal_source_w_per_degree\":300,\"room_source_power\":1.5}").getAsJsonObject());
        assertEquals(300, explicit.roomSourcePower());
    }
}
