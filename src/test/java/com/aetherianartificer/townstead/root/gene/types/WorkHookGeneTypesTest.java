package com.aetherianartificer.townstead.root.gene.types;

import com.aetherianartificer.townstead.pheno.action.ActionTypes;
import com.aetherianartificer.townstead.pheno.action.block.types.AccelerateBlockActionType;
import com.aetherianartificer.townstead.pheno.action.types.ChangeResourceActionType;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WorkHookGeneTypesTest {

    @BeforeAll static void registerAction() { ActionTypes.register(new ChangeResourceActionType()); }

    private static JsonObject json(String raw) {
        return JsonParser.parseString(raw).getAsJsonObject();
    }

    @Test void whenWorkKeepsItsVerbFilter() {
        var parsed = (TriggerGeneType.Instance) new TriggerGeneType().parse(json("""
                {"type":"pheno:trigger","trigger":"when_work","verbs":["townstead:harvested","townstead:tilled"],
                 "action":{"type":"pheno:change_resource","resource":"test:grit","amount":1}}
                """));
        assertNotNull(parsed);
        assertEquals(TriggerGeneType.Trigger.WHEN_WORK, parsed.trigger());
        assertEquals(List.of("townstead:harvested", "townstead:tilled"), parsed.verbs());
        assertTrue(parsed.acceptsVerb("townstead:tilled"));
        assertFalse(parsed.acceptsVerb("townstead:fished"));
    }

    @Test void whenWorkWithoutVerbsAcceptsAll() {
        var single = (TriggerGeneType.Instance) new TriggerGeneType().parse(json("""
                {"type":"pheno:trigger","trigger":"work","verbs":"townstead:fished",
                 "action":{"type":"pheno:change_resource","resource":"test:grit","amount":1}}
                """));
        assertNotNull(single);
        assertTrue(single.acceptsVerb("townstead:fished"));
        var open = (TriggerGeneType.Instance) new TriggerGeneType().parse(json("""
                {"type":"pheno:trigger","trigger":"when_work",
                 "action":{"type":"pheno:change_resource","resource":"test:grit","amount":1}}
                """));
        assertTrue(open.acceptsVerb("anything"));
    }

    @Test void whenWorkAcceptsAnOutputFilter() {
        var parsed = (TriggerGeneType.Instance) new TriggerGeneType().parse(json("""
                {"type":"pheno:trigger","trigger":"when_work",
                 "item_condition":{"type":"pheno:constant","value":true},
                 "action":{"type":"pheno:change_resource","resource":"test:grit","amount":1}}
                """));
        assertNotNull(parsed);
        assertNotNull(parsed.itemCondition());
    }

    @Test void workModifierTargetsParse() {
        for (String target : List.of("durability_loss", "fishing_lure", "fishing_luck", "anvil_break_chance",
                "anvil_material_repair", "anvil_prior_work", "farmland_trample")) {
            var parsed = (ModifierGeneType.Instance) new ModifierGeneType().parse(json(
                    "{\"target\":\"" + target + "\",\"operation\":\"multiply\",\"value\":0.5}"));
            assertNotNull(parsed, target);
            assertNull(parsed.itemCondition(), target);
            assertNull(parsed.aura(), target);
        }
    }

    @Test void itemScopedModifierNeverAppliesWithoutAnItem() {
        var scoped = (ModifierGeneType.Instance) new ModifierGeneType().parse(json("""
                {"target":"durability_loss","operation":"multiply","value":0,
                 "item_condition":{"type":"pheno:constant","value":true}}
                """));
        assertNotNull(scoped);
        assertFalse(scoped.appliesToItem(null, null));
        var open = (ModifierGeneType.Instance) new ModifierGeneType().parse(json(
                "{\"target\":\"durability_loss\",\"value\":0}"));
        assertTrue(open.appliesToItem(null, null));
    }

    @Test void malformedItemFilterFailsClosed() {
        assertNull(new ModifierGeneType().parse(json("""
                {"target":"durability_loss","value":0,"item_condition":{"type":"unknown"}}
                """)));
    }

    @Test void auraParsesWithDefaultsAndACap() {
        var aura = (ModifierGeneType.Instance) new ModifierGeneType().parse(json("""
                {"target":"farmland_trample","operation":"set","value":0,"applies_to":{"radius":40,"self":false}}
                """));
        assertNotNull(aura.aura());
        assertEquals(ModifierGeneType.Aura.MAX_RADIUS, aura.aura().radius());
        assertFalse(aura.aura().includeSelf());
        var defaults = (ModifierGeneType.Instance) new ModifierGeneType().parse(json(
                "{\"target\":\"farmland_trample\",\"value\":0,\"applies_to\":{}}"));
        assertEquals(8, defaults.aura().radius());
        assertTrue(defaults.aura().includeSelf());
    }

    @Test void workBlockConditionsParseAndFailClosed() {
        var conditions = com.aetherianartificer.townstead.pheno.condition.block.BlockConditions.class;
        assertNotNull(com.aetherianartificer.townstead.pheno.condition.block.BlockConditions.parse(
                json("{\"type\":\"pheno:mature\"}")));
        assertNotNull(com.aetherianartificer.townstead.pheno.condition.block.BlockConditions.parse(
                json("{\"type\":\"pheno:farmland\"}")));
        assertNotNull(com.aetherianartificer.townstead.pheno.condition.block.BlockConditions.parse(
                json("{\"type\":\"pheno:work_station\",\"profession\":\"townstead:cook\",\"task\":\"townstead_work:chop\"}")));
        assertNull(com.aetherianartificer.townstead.pheno.condition.block.BlockConditions.parse(
                json("{\"type\":\"pheno:work_station\"}")), "a station needs its profession");
        assertNull(com.aetherianartificer.townstead.pheno.condition.block.BlockConditions.parse(
                json("{\"type\":\"pheno:in_work_area\",\"area\":\"test:unknown\"}")), "unknown area fails closed");
        assertNotNull(conditions);
    }

    @Test void destroyCanCollect() {
        assertNotNull(new com.aetherianartificer.townstead.pheno.action.block.types.DestroyBlockActionType()
                .parse(json("{\"collect\":true}")));
    }

    @Test void accelerateParses() {
        assertNotNull(new AccelerateBlockActionType().parse(json("{\"ticks\":5}")));
        assertNotNull(new AccelerateBlockActionType().parse(json("{}")));
    }
}
