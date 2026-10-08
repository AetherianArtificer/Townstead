package com.aetherianartificer.townstead.clothing.policy;

import com.aetherianartificer.townstead.clothing.ClothingLayer;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WardrobePolicyTest {

    static ResourceLocation id(String s) {
        return com.aetherianartificer.townstead.data.DataPackLang.parseId(s);
    }

    static JsonObject json(String s) {
        return JsonParser.parseString(s).getAsJsonObject();
    }

    @Test
    void parsesScopePriorityAndEveryLayerRule() {
        WardrobePolicy policy = WardrobePolicy.parse(id("test:winter"), json("""
                { "schema": "townstead:wardrobe_policy/v1", "scope": "culture", "priority": 5,
                  "cultures": ["tc:highhold"],
                  "layers": {
                    "base": { "set": "<culture>" },
                    "outerwear": { "require": "required", "select": { "thermal": "warm", "slot": "body" } },
                    "accessory": { "require": "none" }
                  } }
                """));
        assertNotNull(policy);
        assertEquals(WardrobePolicy.Scope.CULTURE, policy.scope());
        assertEquals(5, policy.priority());
        assertEquals(java.util.Set.of(id("tc:highhold")), policy.cultures());
        assertNull(policy.when());

        WardrobePolicy.LayerRule base = policy.rule(ClothingLayer.BASE);
        assertNotNull(base);
        assertEquals(WardrobePolicy.Requirement.REQUIRED, base.requirement());
        assertTrue(base.selector().cultureSets());

        WardrobePolicy.LayerRule outer = policy.rule(ClothingLayer.OUTERWEAR);
        assertNotNull(outer);
        assertEquals(WardrobePolicy.Requirement.REQUIRED, outer.requirement());
        assertNotNull(outer.selector().query());
        assertFalse(outer.selector().query().isEmpty());

        WardrobePolicy.LayerRule accessory = policy.rule(ClothingLayer.ACCESSORY);
        assertNotNull(accessory);
        assertEquals(WardrobePolicy.Requirement.NONE, accessory.requirement());
        assertTrue(accessory.selector().query().isEmpty());
        assertNull(policy.rule(ClothingLayer.ARMOUR));
    }

    @Test
    void aNoneRuleKeepsItsSelector() {
        WardrobePolicy policy = WardrobePolicy.parse(id("test:mild"), json("""
                { "layers": { "outerwear": { "require": "none", "select": { "thermal": "warm" } } } }
                """));
        assertNotNull(policy);
        WardrobePolicy.LayerRule outer = policy.rule(ClothingLayer.OUTERWEAR);
        assertNotNull(outer);
        assertEquals(WardrobePolicy.Requirement.NONE, outer.requirement());
        assertEquals(com.aetherianartificer.townstead.clothing.ClothingQuery.Thermal.WARM,
                outer.selector().query().thermal());
    }

    @Test
    void accessoryDefaultsToPreferredAndBaseToRequired() {
        WardrobePolicy policy = WardrobePolicy.parse(id("test:p"), json("""
                { "layers": { "base": { "skin": "tc:highhold/" }, "accessory": { "select": {} } } }
                """));
        assertNotNull(policy);
        assertEquals(WardrobePolicy.Scope.VILLAGE, policy.scope());
        assertEquals(WardrobePolicy.Requirement.REQUIRED, policy.rule(ClothingLayer.BASE).requirement());
        assertEquals(WardrobePolicy.Requirement.PREFERRED, policy.rule(ClothingLayer.ACCESSORY).requirement());
        assertTrue(policy.rule(ClothingLayer.BASE).selector().skinMatches("tc:highhold/winter_1"));
        assertFalse(policy.rule(ClothingLayer.BASE).selector().skinMatches("tc:ashmarch/1"));
    }

    @Test
    void refusesArmourRulesUnknownScopesAndEmptyBase() {
        assertNull(WardrobePolicy.parse(id("test:a"), json(
                "{ \"layers\": { \"armour\": { \"select\": {} } } }")));
        assertNull(WardrobePolicy.parse(id("test:b"), json(
                "{ \"scope\": \"planet\", \"layers\": { \"base\": { \"skin\": \"x:y\" } } }")));
        assertNull(WardrobePolicy.parse(id("test:c"), json(
                "{ \"scope\": \"culture\", \"layers\": { \"base\": { \"skin\": \"x:y\" } } }")));
        assertNull(WardrobePolicy.parse(id("test:d"), json("{ \"layers\": { \"base\": {} } }")));
        assertNull(WardrobePolicy.parse(id("test:e"), json("{ \"layers\": {} }")));
        assertNull(WardrobePolicy.parse(id("test:f"), json(
                "{ \"layers\": { \"base\": { \"set\": \"a:b\", \"skin\": \"c:d\" } } }")));
    }

    @Test
    void scopeRankOrdersTiesNarrowestFirst() {
        WardrobePolicy village = WardrobePolicy.parse(id("test:v"), json(
                "{ \"scope\": \"village\", \"layers\": { \"base\": { \"skin\": \"a:b\" } } }"));
        WardrobePolicy culture = WardrobePolicy.parse(id("test:c"), json(
                "{ \"scope\": \"culture\", \"cultures\": \"x:y\", \"layers\": { \"base\": { \"skin\": \"a:b\" } } }"));
        WardrobePolicy louder = WardrobePolicy.parse(id("test:l"), json(
                "{ \"scope\": \"village\", \"priority\": 1, \"layers\": { \"base\": { \"skin\": \"a:b\" } } }"));
        assertTrue(WardrobeResolver.beats(culture, village));
        assertFalse(WardrobeResolver.beats(village, culture));
        assertTrue(WardrobeResolver.beats(louder, culture));
        assertFalse(WardrobeResolver.beats(village, village));
    }
}
