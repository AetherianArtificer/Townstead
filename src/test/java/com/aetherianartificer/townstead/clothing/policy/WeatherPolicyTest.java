package com.aetherianartificer.townstead.clothing.policy;

import com.aetherianartificer.townstead.clothing.ClothingEntry;
import com.aetherianartificer.townstead.clothing.ClothingLayer;
import com.aetherianartificer.townstead.clothing.Weather;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeatherPolicyTest {

    static ResourceLocation id(String s) {
        return com.aetherianartificer.townstead.data.DataPackLang.parseId(s);
    }

    static ClothingEntry entry(String body) {
        ClothingEntry parsed = ClothingEntry.parse(id("t:d"), 0, JsonParser.parseString(body).getAsJsonObject());
        assertNotNull(parsed);
        return parsed;
    }

    static final ClothingEntry COAT = entry(
            "{ \"item\": \"wp:coat\", \"layer\": \"outerwear\", \"slot\": \"body\", \"thermal\": { \"offset\": 0.5 } }");
    static final ClothingEntry SUN_HAT = entry(
            "{ \"item\": \"acc:sun_hat\", \"layer\": \"accessory\", \"slot\": \"head\", \"thermal\": { \"heat_resistance\": 0.5 } }");

    @Test
    void coldWantsWarmEverywhereAndHotShedsWarmOuterwear() {
        WardrobePolicy cold = WeatherPolicy.of(Weather.Kind.COLD);
        assertEquals(WardrobePolicy.Scope.VILLAGE, cold.scope());
        assertEquals(WeatherPolicy.PRIORITY, cold.priority());
        assertTrue(WeatherPolicy.isWeather(cold));
        assertEquals(WardrobePolicy.Requirement.REQUIRED, cold.rule(ClothingLayer.BASE).requirement());
        assertEquals(WardrobePolicy.Requirement.REQUIRED, cold.rule(ClothingLayer.OUTERWEAR).requirement());
        assertTrue(cold.rule(ClothingLayer.OUTERWEAR).selector().query().test(COAT));
        assertFalse(cold.rule(ClothingLayer.OUTERWEAR).selector().query().test(SUN_HAT));
        assertEquals(WardrobePolicy.Requirement.PREFERRED, cold.rule(ClothingLayer.ACCESSORY).requirement());
        assertNull(cold.rule(ClothingLayer.ARMOUR));

        WardrobePolicy hot = WeatherPolicy.of(Weather.Kind.HOT);
        assertEquals(WardrobePolicy.Requirement.NONE, hot.rule(ClothingLayer.OUTERWEAR).requirement());
        assertTrue(hot.rule(ClothingLayer.OUTERWEAR).selector().query().test(COAT));
        assertTrue(hot.rule(ClothingLayer.ACCESSORY).selector().query().test(SUN_HAT));

        WardrobePolicy mild = WeatherPolicy.of(Weather.Kind.MILD);
        assertNull(mild.rule(ClothingLayer.BASE));
        assertEquals(WardrobePolicy.Requirement.NONE, mild.rule(ClothingLayer.OUTERWEAR).requirement());
        assertSame(mild, WeatherPolicy.of(null));
    }

    @Test
    void theDefaultFillsOnlyLayersDataLeftOpen() {
        WardrobePolicy data = WardrobePolicy.parse(id("t:festival"), JsonParser.parseString(
                "{ \"layers\": { \"outerwear\": { \"require\": \"preferred\", \"select\": { \"occasion\": \"festival\" } } } }")
                .getAsJsonObject());
        assertNotNull(data);
        Map<ClothingLayer, WardrobePolicy.LayerRule> rules = new EnumMap<>(ClothingLayer.class);
        Map<ClothingLayer, WardrobePolicy> sources = new EnumMap<>(ClothingLayer.class);
        rules.put(ClothingLayer.OUTERWEAR, data.rule(ClothingLayer.OUTERWEAR));
        sources.put(ClothingLayer.OUTERWEAR, data);

        WardrobeResolver.fill(rules, sources, WeatherPolicy.of(Weather.Kind.COLD));

        assertSame(data, sources.get(ClothingLayer.OUTERWEAR));
        assertEquals(WardrobePolicy.Requirement.PREFERRED, rules.get(ClothingLayer.OUTERWEAR).requirement());
        assertTrue(WeatherPolicy.isWeather(sources.get(ClothingLayer.BASE)));
        assertTrue(WeatherPolicy.isWeather(sources.get(ClothingLayer.ACCESSORY)));
        assertEquals(3, rules.size());
    }
}
