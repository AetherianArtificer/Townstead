package com.aetherianartificer.townstead.temperature;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ThermalApplianceTest {
    @Test void explicitPowerDoesNotDependOnTheGlobalDegreeConversion() {
        var settings=TemperatureSettings.parse(JsonParser.parseString("{\"thermal_source_w_per_degree\":1}").getAsJsonObject());
        var heater=settings.appliance("legendarysurvivaloverhaul:heater");
        assertEquals(3750,heater.output(15,true).watts());
        assertEquals(0,heater.output(0,true).watts());
        assertEquals(0,heater.output(15,false).watts());
        assertEquals(1875,heater.output(7.5f,true).watts());
        assertEquals(0,settings.appliance("legendarysurvivaloverhaul:heater_top").output(Float.NaN,true).watts());
        var fire=settings.appliance("minecraft:campfire");
        assertEquals(fire.output(10,true).watts(),fire.output(25,true).watts(),
                "Switching backend temperature scales must not multiply an explicitly rated vanilla appliance");
    }
    @Test void packCanSeparateRadianceFromDeliveredPower() {
        var profile=ThermalAppliance.parse(JsonParser.parseString("{\"room_power_w\":5000,\"radiant_degrees\":3}").getAsJsonObject());
        assertEquals(5000,profile.output(15,true).watts());
        assertEquals(3,profile.output(15,true).radiantDegrees());
        assertFalse(profile.output(15,true).estimated());
        assertThrows(IllegalArgumentException.class,()->new ThermalAppliance(Double.NaN,3,15));
    }
}

