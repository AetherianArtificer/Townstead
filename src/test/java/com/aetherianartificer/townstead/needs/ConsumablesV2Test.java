package com.aetherianartificer.townstead.needs;

import com.aetherianartificer.townstead.food.ConsumptionPolicy;
import com.aetherianartificer.townstead.pheno.action.ActionTypes;
import com.aetherianartificer.townstead.pheno.action.types.NothingActionType;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

class ConsumablesV2Test {
    @BeforeAll
    static void registerAction() {
        ActionTypes.register(new NothingActionType());
    }

    @Test
    void v1RemainsAnEffectsDocument() {
        Consumables.Definition definition = parse("""
                {
                  "schema":"townstead:consumable/v1",
                  "items":["example:tea"],
                  "effects":{"type":"pheno:nothing"}
                }
                """);

        assertNotNull(definition);
        assertNotNull(definition.effects());
        assertNull(definition.transaction());
    }

    @Test
    void v2AddsAnOptionalTransactionToTheSameDocument() {
        Consumables.Definition definition = parse("""
                {
                  "schema":"townstead:consumable/v2",
                  "items":["example:coffee_pot"],
                  "effects":{"type":"pheno:nothing"},
                  "transaction":{
                    "mode":"replace_with_pheno",
                    "consumers":["villager"],
                    "servings":4
                  }
                }
                """);

        assertNotNull(definition);
        assertNotNull(definition.effects());
        assertEquals(4, definition.transaction().servings());
        assertEquals(ConsumptionPolicy.Mode.REPLACE_WITH_PHENO,
                definition.transaction().mode());
    }

    @Test
    void v2MayDescribeNativeTransactionWithoutInventingNeedEffects() {
        Consumables.Definition definition = parse("""
                {
                  "schema":"townstead:consumable/v2",
                  "items":["example:ale"],
                  "transaction":{"mode":"observe_native"}
                }
                """);

        assertNotNull(definition);
        assertNull(definition.effects());
        assertEquals(NeedEffectProjection.NONE, definition.projection());
        assertEquals(ConsumptionPolicy.Mode.OBSERVE_NATIVE, definition.transaction().mode());
    }

    @Test
    void transactionRequiresV2AndUnknownSchemasFailClosed() {
        assertNull(parse("""
                {
                  "schema":"townstead:consumable/v1",
                  "items":["example:tea"],
                  "effects":{"type":"pheno:nothing"},
                  "transaction":{}
                }
                """));
        assertThrows(IllegalArgumentException.class, () -> parse("""
                {"schema":"townstead:consumable/v9","items":["example:tea"]}
                """));
    }

    @Test
    void disjointConsumerDefinitionsMayShareAnItemSelector() {
        Consumables.Definition player = parse("""
                {
                  "schema":"townstead:consumable/v2",
                  "items":["example:ale"],
                  "transaction":{"consumers":["player"]}
                }
                """);
        Consumables.Definition villager = parse("""
                {
                  "schema":"townstead:consumable/v2",
                  "items":["example:ale"],
                  "transaction":{"mode":"replace_with_pheno","consumers":["villager"]}
                }
                """);

        assertEquals(2, Consumables.withoutAmbiguities(List.of(player, villager)).size());
    }

    @Test
    void overlappingConsumerDefinitionsRejectTheLaterDefinition() {
        Consumables.Definition first = parse("""
                {
                  "schema":"townstead:consumable/v2",
                  "items":["example:ale"],
                  "transaction":{"consumers":["player","villager"]}
                }
                """);
        Consumables.Definition later = parse("""
                {
                  "schema":"townstead:consumable/v2",
                  "items":["example:ale"],
                  "transaction":{"consumers":["villager"]}
                }
                """);

        assertEquals(List.of(first), Consumables.withoutAmbiguities(List.of(first, later)));
    }

    @Test
    void resolverSelectsTheDefinitionForTheRequestedConsumer() {
        Consumables.Definition player = parseFor("townstead:player_ale", """
                {
                  "schema":"townstead:consumable/v2",
                  "items":["minecraft:apple"],
                  "transaction":{"consumers":["player"]}
                }
                """);
        Consumables.Definition villager = parseFor("townstead:villager_ale", """
                {
                  "schema":"townstead:consumable/v2",
                  "items":["minecraft:apple"],
                  "transaction":{"mode":"replace_with_pheno","consumers":["villager"]}
                }
                """);
        List<Consumables.Definition> definitions = Consumables.withoutAmbiguities(
                List.of(player, villager));

        ResourceLocation apple = ResourceLocation.tryParse("minecraft:apple");
        assertEquals(player, Consumables.resolveExact(definitions, apple,
                ConsumptionPolicy.Consumer.PLAYER));
        assertEquals(villager, Consumables.resolveExact(definitions, apple,
                ConsumptionPolicy.Consumer.VILLAGER));
        assertNull(Consumables.resolveExact(definitions, apple,
                ConsumptionPolicy.Consumer.OTHER_LIVING));
    }

    private static Consumables.Definition parse(String json) {
        return parseFor("townstead:test", json);
    }

    private static Consumables.Definition parseFor(String id, String json) {
        return Consumables.parse(ResourceLocation.tryParse(id),
                JsonParser.parseString(json).getAsJsonObject());
    }
}
