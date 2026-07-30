package com.aetherianartificer.townstead.work.order;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * What a stored order becomes when it is read back by a build that does not recognise it. The NBT
 * walk itself is untestable here (the test source set stubs CompoundTag), so the decisions inside
 * it live in these two parsers.
 */
class OrderParseTest {

    @Test
    void knownModesRoundTrip() {
        for (Order.Mode mode : Order.Mode.values()) {
            assertEquals(mode, Order.Mode.parse(mode.name()));
        }
        assertEquals(Order.Mode.KEEP_STOCKED, Order.Mode.parse("keep_stocked"),
                "case must not decide whether a kitchen keeps bread on the shelf");
    }

    @Test
    void anUnknownModeFallsBackToTheHarmlessOne() {
        assertEquals(Order.Mode.STANDING, Order.Mode.parse("make_forever_somehow"));
        assertEquals(Order.Mode.STANDING, Order.Mode.parse(null));
        assertEquals(Order.Mode.STANDING, Order.Mode.parse(""));
    }

    @Test
    void theFallbackModeIsTheOneWithNoTarget() {
        assertFalse(Order.Mode.STANDING.hasTarget(),
                "an unreadable line must not inherit somebody else's number and start producing to it");
    }

    @Test
    void onlyCountedProductionModesCountProduction() {
        assertTrue(Order.Mode.MAKE.countsProduction());
        assertFalse(Order.Mode.KEEP_STOCKED.countsProduction());
        assertFalse(Order.Mode.PER_VILLAGER.countsProduction());
        assertFalse(Order.Mode.STANDING.countsProduction());
    }

    @Test
    void unknownScopeCountsNarrowly() {
        assertEquals(Order.CountScope.VILLAGE, Order.CountScope.parse("VILLAGE"));
        assertEquals(Order.CountScope.VILLAGE, Order.CountScope.parse("village"));
        assertEquals(Order.CountScope.HERE, Order.CountScope.parse("somewhere_else"));
        assertEquals(Order.CountScope.HERE, Order.CountScope.parse(null),
                "counting less over-produces; counting more would quietly starve the village");
    }

    @Test
    void aReloadedLineStartsWithNothingInProgress() {
        // What load() reconstructs: everything but inProgress, which means "a worker is mid-job".
        Order order = new Order(id(), Order.Mode.MAKE, 10);
        order.setProduced(4);

        assertEquals(0, order.inProgress(),
                "persisting a claim would leave a line held forever by a villager who is not working");
        assertEquals(6, order.outstanding(new Ctx()));
    }

    private static final class Ctx implements OrderContext {
        @Override public int stockOf(net.minecraft.resources.ResourceLocation item, Order.CountScope scope) { return 0; }
        @Override public int villagerCount() { return 1; }
        @Override public boolean mayWork(Order order) { return true; }
    }

    private static net.minecraft.resources.ResourceLocation id() {
        //? if >=1.21 {
        return net.minecraft.resources.ResourceLocation.parse("minecraft:bread");
        //?} else {
        /*return new net.minecraft.resources.ResourceLocation("minecraft:bread");
        *///?}
    }
}
