package com.aetherianartificer.townstead.root.gene.types;

import com.aetherianartificer.townstead.pheno.action.ActionTypes;
import com.aetherianartificer.townstead.pheno.action.types.ChangeResourceActionType;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TriggerItemConditionTest {
    @BeforeAll static void registerAction() { ActionTypes.register(new ChangeResourceActionType()); }

    private static JsonObject trigger() {
        return JsonParser.parseString("""
                {"type":"pheno:trigger","trigger":"when_item_use",
                 "action":{"type":"pheno:change_resource","resource":"test:cured","operation":"set","amount":1}}
                """).getAsJsonObject();
    }

    @Test void itemUseRetainsItsItemPredicate() {
        JsonObject json = trigger();
        json.add("item_condition", JsonParser.parseString("{\"type\":\"pheno:constant\",\"value\":false}"));
        var parsed = (TriggerGeneType.Instance) new TriggerGeneType().parse(json);
        assertNotNull(parsed);
        assertEquals(TriggerGeneType.Trigger.WHEN_ITEM_USE, parsed.trigger());
        assertFalse(parsed.itemCondition().test(null, null));
    }

    @Test void malformedOrMisplacedItemFiltersFailClosed() {
        JsonObject json = trigger();
        json.add("item_condition", JsonParser.parseString("{\"type\":\"unknown\"}"));
        assertNull(new TriggerGeneType().parse(json));
        json.add("item_condition", JsonParser.parseString("{\"type\":\"pheno:constant\",\"value\":true}"));
        json.addProperty("trigger", "when_hurt");
        assertNull(new TriggerGeneType().parse(json));
    }

    @Test void unfilteredExistingTriggersRemainValid() {
        var parsed = (TriggerGeneType.Instance) new TriggerGeneType().parse(trigger());
        assertNotNull(parsed);
        assertNull(parsed.itemCondition());
    }

    @Test void dimensionEntryRetainsItsDestinationCondition() {
        com.aetherianartificer.townstead.pheno.condition.ConditionTypes.register(
                new com.aetherianartificer.townstead.pheno.condition.types.DimensionConditionType());
        JsonObject json = trigger();
        json.addProperty("trigger", "when_enter_dimension");
        json.add("condition", JsonParser.parseString("""
                {"type":"pheno:dimension","dimension":"minecraft:overworld"}
                """));
        var parsed = (TriggerGeneType.Instance) new TriggerGeneType().parse(json);
        assertNotNull(parsed);
        assertEquals(TriggerGeneType.Trigger.WHEN_ENTER_DIMENSION, parsed.trigger());
        assertNotNull(parsed.condition());
        assertNull(parsed.itemCondition());
    }

    @Test void cureFlagDoesNotRegenerateAndSurvivesDeathWithoutAHud() {
        var parsed = (ResourceGeneType.Instance) new ResourceGeneType().parse(JsonParser.parseString("""
                {"min":0,"max":1,"start":0,"regen":0,"persist_on_death":true,
                 "display":{"visibility":"never"}}
                """).getAsJsonObject());
        assertEquals(0, parsed.start());
        assertEquals(1, parsed.max());
        assertEquals(0, parsed.regen());
        assertTrue(parsed.persistOnDeath());
        assertEquals(ResourceDisplay.Eligibility.NEVER, parsed.resourceDisplay().eligibility());
    }
}
