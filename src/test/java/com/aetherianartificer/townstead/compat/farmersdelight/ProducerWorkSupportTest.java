package com.aetherianartificer.townstead.compat.farmersdelight;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProducerWorkSupportTest {

    private record FakeRecipe(String id, String output, boolean beverage) {}

    @Test
    void baristaRoleIsBeveragesOnly() {
        assertTrue(ProducerWorkSupport.beveragesOnly(ProducerRole.BARISTA));
    }

    @Test
    void matchSessionValuePrefersExactRecipeId() {
        FakeRecipe coffee = new FakeRecipe("coffee_a", "coffee", true);
        FakeRecipe altCoffee = new FakeRecipe("coffee_b", "coffee", true);

        FakeRecipe matched = ProducerWorkSupport.matchSessionValue(
                List.of(coffee, altCoffee),
                "coffee_b",
                "coffee",
                FakeRecipe::id,
                FakeRecipe::output
        );

        assertSame(altCoffee, matched);
    }

    @Test
    void matchSessionValueFallsBackToOutputId() {
        FakeRecipe stew = new FakeRecipe("stew", "mushroom_stew", false);

        FakeRecipe matched = ProducerWorkSupport.matchSessionValue(
                List.of(stew),
                "missing",
                "mushroom_stew",
                FakeRecipe::id,
                FakeRecipe::output
        );

        assertSame(stew, matched);
    }

    @Test
    void matchSessionValueReturnsNullWhenOutputMissing() {
        FakeRecipe stew = new FakeRecipe("stew", "mushroom_stew", false);

        assertNull(ProducerWorkSupport.matchSessionValue(
                List.of(stew),
                "missing",
                "bread",
                FakeRecipe::id,
                FakeRecipe::output
        ));
    }

    @Test
    void fakeRecipePreservesBeverageMetadata() {
        FakeRecipe coffee = new FakeRecipe("coffee", "coffee", true);
        assertEquals("coffee", coffee.output());
        assertTrue(coffee.beverage());
    }
}
