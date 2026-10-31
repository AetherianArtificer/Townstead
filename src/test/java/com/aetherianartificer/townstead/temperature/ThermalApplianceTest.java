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
    @Test void aReversibleApplianceHeatsOrCoolsByItsNativeSign() {
        var settings=TemperatureSettings.parse(JsonParser.parseString("{}").getAsJsonObject());
        var hearth=settings.appliance("cold_sweat:hearth_bottom");
        assertEquals(4000,hearth.output(1,true).watts());
        assertEquals(-4000,hearth.output(-1,true).watts());
        assertEquals(0,hearth.output(0,true).watts());
        assertEquals(-3000,settings.appliance("cold_sweat:icebox").output(-1,true).watts());
        assertEquals(0,settings.appliance("cold_sweat:boiler").output(-1,true).watts(),
                "A one-way appliance never runs backwards");
    }
    @Test void firesDraftByDefaultAndControlledHeatersOptOut() {
        assertTrue(ThermalAppliance.parse(JsonParser.parseString("{\"room_power_w\":2500}").getAsJsonObject()).draft());
        assertFalse(ThermalAppliance.parse(JsonParser.parseString("{\"room_power_w\":2500,\"draft\":false}").getAsJsonObject()).draft());
        var settings=TemperatureSettings.parse(JsonParser.parseString("{}").getAsJsonObject());
        assertTrue(settings.appliance("minecraft:campfire").draft());
        assertFalse(settings.appliance("legendarysurvivaloverhaul:heater").draft());
        assertEquals(25,settings.fireMaxRise());
    }
    @Test void radiantHeatComesFromTheSurfaceUnlessOverridden() {
        var hearth=TemperatureSettings.get().appliance("cold_sweat:hearth_bottom");
        assertEquals(19.2,hearth.output(1,true,12).radiantDegrees(),0.1,"a hearth radiates about 19 at its face");
        assertTrue(hearth.output(1,true,30).radiantDegrees()<hearth.output(1,true,0).radiantDegrees(),
                "a hot face stands out less in warm air");
        assertTrue(hearth.output(-1,true,20).radiantDegrees()<0,"the cooling side radiates cold from its own face");
        assertTrue(hearth.output(-1,true,20).radiantDegrees()>-5,"a cold face chills far less than a fire warms");
        var parsed=ThermalAppliance.parse(JsonParser.parseString("{\"power_w\":2500,\"surface_c\":200}").getAsJsonObject());
        assertEquals(2500,parsed.output(Float.NaN,true).watts());
        assertEquals(0.0667f*185,parsed.output(Float.NaN,true,15).radiantDegrees(),0.05);
        var overridden=ThermalAppliance.parse(JsonParser.parseString("{\"power_w\":2500,\"surface_c\":200,\"radiant_degrees\":5}").getAsJsonObject());
        assertEquals(5,overridden.output(Float.NaN,true,15).radiantDegrees());
    }
    @Test void soulFireIsHeat() {
        var soul=TemperatureSettings.get().appliance("minecraft:soul_campfire");
        var fire=TemperatureSettings.get().appliance("minecraft:campfire");
        assertTrue(soul.output(Float.NaN,true,15).watts()>0);
        assertTrue(soul.output(Float.NaN,true,15).radiantDegrees()>0);
        assertTrue(soul.output(Float.NaN,true,15).radiantDegrees()<fire.output(Float.NaN,true,15).radiantDegrees());
    }
}
