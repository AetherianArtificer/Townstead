package com.aetherianartificer.townstead.work.recipe;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fluid amounts exist to answer the two questions a villager can act on: how many containers must
 * I carry in, and how many servings come out.
 */
class FluidAmountTest {

    private static ResourceLocation id(String raw) {
        return ResourceLocation.tryParse(raw);
    }

    @Test
    void containersRoundUpBecauseYouCannotCarryPartOfABucket() {
        FluidAmount water = new FluidAmount(id("minecraft:water"), FluidAmount.BUCKET);
        assertEquals(1, water.containersNeeded(FluidAmount.BUCKET));

        // A recipe wanting a quarter bucket still needs a whole one brought to it.
        FluidAmount splash = new FluidAmount(id("minecraft:water"), 250);
        assertEquals(1, splash.containersNeeded(FluidAmount.BUCKET));

        FluidAmount bigBatch = new FluidAmount(id("minecraft:water"), 2500);
        assertEquals(3, bigBatch.containersNeeded(FluidAmount.BUCKET));
    }

    @Test
    void portionsRoundDownBecauseAPartialServingIsNotAServing() {
        // Brewin' ferments 1000mB and pours 250mB at a time: four mugs, not four and a bit.
        FluidAmount beer = new FluidAmount(id("brewinandchewin:beer"), FluidAmount.BUCKET);
        assertEquals(4, beer.portions(250));
        assertEquals(0, beer.portions(1500), "less than one serving is no servings");
    }

    @Test
    void degenerateAmountsAreRefusedRatherThanGuessed() {
        assertNull(FluidAmount.of(id("minecraft:water"), 0));
        assertNull(FluidAmount.of(null, 1000));
        assertTrue(new FluidAmount(id("minecraft:water"), -50).isEmpty(),
                "a negative amount is clamped, never a negative requirement");
        assertEquals(0, new FluidAmount(id("minecraft:water"), 1000).portions(0),
                "a zero-sized portion yields nothing rather than dividing by zero");
    }
}
