package com.aetherianartificer.townstead.work.recipe;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two-stage join. A brew that ends as fluid and a pour that starts as fluid are each
 * unworkable alone; paired on the fluid they share they become an ordinary errand.
 */
class FluidRecipesTest {

    private static ResourceLocation id(String raw) {
        return ResourceLocation.tryParse(raw);
    }

    private static RecipeIngredient item(String raw) {
        return new RecipeIngredient(List.of(id(raw)), 1);
    }

    /** Brewin's real beer recipe: a bucket of water plus three items, fermented for 9600 ticks. */
    private static FluidRecipes.Brew beerBrew() {
        return new FluidRecipes.Brew(
                id("brewinandchewin:beer_from_water"),
                List.of(item("minecraft:wheat"), item("minecraft:wheat_seeds"),
                        item("minecraft:brown_mushroom")),
                new FluidAmount(id("minecraft:water"), FluidAmount.BUCKET),
                new FluidAmount(id("brewinandchewin:beer"), FluidAmount.BUCKET),
                9600);
    }

    private static FluidRecipes.Pour beerPour(int amount, @Nullable String container) {
        return new FluidRecipes.Pour(id("brewinandchewin:pour_beer"), id("brewinandchewin:beer"),
                amount, id("brewinandchewin:beer"), container == null ? null : id(container));
    }

    @BeforeEach
    void carriers() {
        FluidCarriers.bootstrap();
    }

    @Test
    void aBrewAndItsPourBecomeOneOrdinaryRecipe() {
        List<DiscoveredRecipe> joined = FluidRecipes.join(
                List.of(beerBrew()), List.of(beerPour(250, null)), StationType.PASSIVE_STATION, 3);

        assertEquals(1, joined.size());
        DiscoveredRecipe beer = joined.get(0);
        assertEquals(id("brewinandchewin:beer"), beer.output());
        assertEquals(4, beer.outputCount(), "1000mB poured 250 at a time is four servings");
        assertEquals(9600, beer.cookTimeTicks());
        assertEquals(StationType.PASSIVE_STATION, beer.stationType());

        // The three grain items, plus a water bucket standing in for the base fluid.
        assertEquals(4, beer.inputs().size());
        assertTrue(beer.inputs().stream().anyMatch(i -> i.itemIds().contains(id("minecraft:water_bucket"))),
                "the base fluid must arrive as something a villager can carry");
    }

    @Test
    void aPourNeedingAVesselAsksForOnePerServing() {
        List<DiscoveredRecipe> joined = FluidRecipes.join(
                List.of(beerBrew()), List.of(beerPour(250, "minecraft:glass_bottle")),
                StationType.PASSIVE_STATION, 3);

        RecipeIngredient bottles = joined.get(0).inputs().stream()
                .filter(i -> i.itemIds().contains(id("minecraft:glass_bottle")))
                .findFirst().orElseThrow();
        assertEquals(4, bottles.count(), "four servings need four vessels");
    }

    @Test
    void aBrewNobodyCanEmptyIsDroppedRatherThanOffered() {
        // Filling a station that cannot be drained is a trap, not a job.
        List<DiscoveredRecipe> joined = FluidRecipes.join(
                List.of(beerBrew()),
                List.of(new FluidRecipes.Pour(id("x:pour_cider"), id("othermod:cider"), 250,
                        id("othermod:cider"), null)),
                StationType.PASSIVE_STATION, 3);
        assertTrue(joined.isEmpty());
    }

    @Test
    void aBrewYieldingLessThanOneServingIsDropped() {
        FluidRecipes.Brew dribble = new FluidRecipes.Brew(
                id("x:dribble"), List.of(item("minecraft:wheat")), null,
                new FluidAmount(id("brewinandchewin:beer"), 100), 200);
        assertTrue(FluidRecipes.join(List.of(dribble), List.of(beerPour(250, null)),
                StationType.PASSIVE_STATION, 1).isEmpty());
    }

    @Test
    void anUncarriableBaseFluidDropsTheRecipe() {
        FluidRecipes.Brew exotic = new FluidRecipes.Brew(
                id("x:exotic"), List.of(item("minecraft:wheat")),
                new FluidAmount(id("othermod:liquid_starlight"), FluidAmount.BUCKET),
                new FluidAmount(id("brewinandchewin:beer"), FluidAmount.BUCKET), 400);
        assertTrue(FluidRecipes.join(List.of(exotic), List.of(beerPour(250, null)),
                        StationType.PASSIVE_STATION, 1).isEmpty(),
                "no known carrier means no villager can start this");
    }

    @Test
    void theSmallestPourWinsBecauseItDividesABatchFurthest() {
        List<DiscoveredRecipe> joined = FluidRecipes.join(
                List.of(beerBrew()),
                List.of(beerPour(500, null), beerPour(250, null)),
                StationType.PASSIVE_STATION, 3);
        assertEquals(4, joined.get(0).outputCount());
    }
}
