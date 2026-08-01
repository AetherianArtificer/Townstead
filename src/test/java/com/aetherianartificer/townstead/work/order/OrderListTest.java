package com.aetherianartificer.townstead.work.order;

import com.aetherianartificer.townstead.work.producer.ProducerRecipe;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Orders decide what a villager makes, so the arithmetic has to be exactly right in both
 * directions: a counted-production line must finish, and a stocked line must not.
 */
class OrderListTest {

    private static final ResourceLocation BREAD = id("minecraft:bread");
    private static final ResourceLocation STEW = id("minecraft:beef_stew");
    private static final ResourceLocation PIE = id("minecraft:pumpkin_pie");

    // ── Progress arithmetic ──

    @Test
    void makeCountsProductionAndRetires() {
        Order order = new Order(BREAD, Order.Mode.MAKE, 10);
        Ctx ctx = new Ctx();
        ctx.stock.put(BREAD, 999);  // irrelevant to a counted-production line

        assertEquals(10, order.outstanding(ctx), "storage must not satisfy a 'make' order");
        order.finish(4);
        assertEquals(6, order.outstanding(ctx));
        order.finish(6);
        assertEquals(0, order.outstanding(ctx));
        assertTrue(order.retired(), "a batch that is done stops being a line");
    }

    @Test
    void keepStockedReadsStorageAndNeverRetires() {
        Order order = new Order(BREAD, Order.Mode.KEEP_STOCKED, 20);
        Ctx ctx = new Ctx();
        ctx.stock.put(BREAD, 14);

        assertEquals(6, order.outstanding(ctx));
        order.finish(50);
        assertEquals(6, order.outstanding(ctx),
                "production must not satisfy a stocked line, or it stops refilling");
        assertFalse(order.retired());

        ctx.stock.put(BREAD, 25);
        assertEquals(0, order.outstanding(ctx));
        assertFalse(order.retired(), "a full shelf is quiet, not finished");
    }

    @Test
    void perVillagerScalesWithTheVillage() {
        Order order = new Order(STEW, Order.Mode.PER_VILLAGER, 1);
        Ctx ctx = new Ctx();
        ctx.villagers = 25;
        ctx.stock.put(STEW, 11);

        assertEquals(14, order.outstanding(ctx));
        ctx.villagers = 5;
        assertEquals(0, order.outstanding(ctx), "a smaller village wants less, with no edit");
    }

    @Test
    void standingAlwaysWantsWorkButNeverRetires() {
        Order order = new Order(BREAD, Order.Mode.STANDING, 0);
        Ctx ctx = new Ctx();
        ctx.stock.put(BREAD, 10_000);

        assertTrue(order.wantsWork(ctx));
        assertFalse(order.retired());
    }

    @Test
    void workUnderWayCountsSoTwoCooksDoNotBothMakeTheLastOne() {
        Order order = new Order(PIE, Order.Mode.MAKE, 10);
        Ctx ctx = new Ctx();
        order.finish(9);

        assertEquals(1, order.outstanding(ctx));
        order.claim();
        assertEquals(0, order.outstanding(ctx), "the tenth is spoken for");
        assertFalse(order.wantsWork(ctx));

        order.abandon();
        assertEquals(1, order.outstanding(ctx), "a job that came to nothing frees the line again");
    }

    @Test
    void pausingStopsWorkWithoutLosingProgress() {
        Order order = new Order(BREAD, Order.Mode.MAKE, 10);
        Ctx ctx = new Ctx();
        order.finish(3);
        order.setPaused(true);

        assertFalse(order.wantsWork(ctx));
        assertEquals(7, order.outstanding(ctx), "progress survives a pause");
        order.setPaused(false);
        assertTrue(order.wantsWork(ctx));
    }

    // ── Selection ──

    @Test
    void listPositionIsTheWholePrioritySystem() {
        OrderList list = new OrderList();
        list.add(new Order(BREAD, Order.Mode.MAKE, 5));
        list.add(new Order(STEW, Order.Mode.MAKE, 5));

        OrderList.Pick picked = list.firstWorkable(List.of(recipe(STEW), recipe(BREAD)), new Ctx());
        assertEquals(BREAD, picked.recipe().output(), "the top line wins regardless of candidate order");

        list.move(0, 1);
        assertEquals(STEW, list.firstWorkable(List.of(recipe(STEW), recipe(BREAD)), new Ctx()).recipe().output());
    }

    @Test
    void satisfiedAndPausedLinesAreSkippedNotBlocking() {
        OrderList list = new OrderList();
        Order done = new Order(BREAD, Order.Mode.MAKE, 5);
        done.finish(5);
        Order paused = new Order(STEW, Order.Mode.MAKE, 5);
        paused.setPaused(true);
        list.add(done);
        list.add(paused);
        list.add(new Order(PIE, Order.Mode.MAKE, 5));

        OrderList.Pick picked = list.firstWorkable(
                List.of(recipe(BREAD), recipe(STEW), recipe(PIE)), new Ctx());
        assertEquals(PIE, picked.recipe().output(), "a done or paused line must not stall the ones below it");
    }

    @Test
    void anOrderedItemNobodyCanMakeHereIsSkipped() {
        OrderList list = new OrderList();
        list.add(new Order(PIE, Order.Mode.MAKE, 5));
        list.add(new Order(BREAD, Order.Mode.MAKE, 5));

        OrderList.Pick picked = list.firstWorkable(List.of(recipe(BREAD)), new Ctx());
        assertEquals(BREAD, picked.recipe().output(),
                "an order for something this station cannot make must not block the list");
    }

    @Test
    void nothingApplicableMeansNullSoTheCallerCanFallThrough() {
        OrderList list = new OrderList();
        list.add(new Order(PIE, Order.Mode.MAKE, 5));

        assertNull(list.firstWorkable(List.of(recipe(BREAD)), new Ctx()));
        assertNull(new OrderList().firstWorkable(List.of(recipe(BREAD)), new Ctx()),
                "an empty list means 'carry on', never 'stand still'");
    }

    @Test
    void aWorkerWhoMayNotTakeALineIsPassedOver() {
        OrderList list = new OrderList();
        Order restricted = new Order(BREAD, Order.Mode.MAKE, 5);
        restricted.setMinRank(3);
        list.add(restricted);
        list.add(new Order(STEW, Order.Mode.MAKE, 5));

        Ctx ctx = new Ctx();
        ctx.refuse = restricted;

        assertEquals(STEW, list.firstWorkable(List.of(recipe(BREAD), recipe(STEW)), ctx).recipe().output());
    }

    @Test
    void twoLinesForOneItemAreWorkedTopDownAndCreditedSeparately() {
        // "Make 5 cakes now, and another 5 after" is a real thing to ask for, so the list allows
        // it. What must not happen is the second line's work landing on the first line's tally.
        OrderList list = new OrderList();
        Order urgent = new Order(BREAD, Order.Mode.MAKE, 5);
        Order later = new Order(BREAD, Order.Mode.MAKE, 5);
        list.add(urgent);
        list.add(later);

        OrderList.Pick first = list.firstWorkable(List.of(recipe(BREAD)), new Ctx());
        assertSame(urgent, first.order(), "the top line is the one picked");

        first.order().finish(5);
        assertTrue(urgent.retired());
        assertFalse(later.retired(), "the second line must not inherit the first line's progress");

        OrderList.Pick second = list.firstWorkable(List.of(recipe(BREAD)), new Ctx());
        assertSame(later, second.order(), "once the first retires the second is picked up");
    }

    @Test
    void aClaimOnTheSecondLineIsNotChargedToTheFirst() {
        // The bug this shape exists to prevent: looking a line up by output after choosing it finds
        // the first one with that item, which is the wrong line whenever there are two.
        OrderList list = new OrderList();
        Order satisfied = new Order(BREAD, Order.Mode.MAKE, 1);
        satisfied.finish(1);
        Order working = new Order(BREAD, Order.Mode.MAKE, 5);
        list.add(satisfied);
        list.add(working);

        OrderList.Pick pick = list.firstWorkable(List.of(recipe(BREAD)), new Ctx());
        assertSame(working, pick.order());
        pick.order().claim();
        assertEquals(1, working.inProgress());
        assertEquals(0, satisfied.inProgress(), "the satisfied line must not be claimed");
    }

    @Test
    void sweepingRetiredLinesLeavesTheRest() {
        OrderList list = new OrderList();
        Order done = new Order(BREAD, Order.Mode.MAKE, 2);
        done.finish(2);
        list.add(done);
        list.add(new Order(STEW, Order.Mode.KEEP_STOCKED, 5));

        assertEquals(1, list.sweepRetired());
        assertEquals(1, list.size());
        assertEquals(STEW, list.at(0).output());
    }

    // ── Tag lines ──

    @Test
    void tagLineMatchesMembersAndSumsTheSet() {
        ResourceLocation meats = id("townstead:orders/meats");
        // Membership is injected so the test never loads a registry: bread and stew are "meat".
        OrderTags.resolveWith((tag, item) ->
                tag.equals(meats) && (item.equals(BREAD) || item.equals(STEW)));
        try {
            OrderList orders = new OrderList();
            orders.add(new Order(meats, Order.Kind.TAG, Order.Mode.KEEP_STOCKED, 10));
            Ctx ctx = new Ctx();
            ctx.tagStock.put(meats, 4);

            OrderList.Pick pick = orders.firstWorkable(List.of(recipe(PIE), recipe(STEW)), ctx);
            assertNotNull(pick, "a candidate in the set must satisfy the line");
            assertEquals(STEW, pick.recipe().output(),
                    "the non-member must be passed over, in candidate order");

            ctx.tagStock.put(meats, 12);
            assertNull(orders.firstWorkable(List.of(recipe(STEW)), ctx),
                    "a set counted full goes quiet like any stocked line");
        } finally {
            OrderTags.resolveWith(null);
        }
    }

    // ── Helpers ──

    private static final class Ctx implements OrderContext {
        final Map<ResourceLocation, Integer> stock = new HashMap<>();
        final Map<ResourceLocation, Integer> tagStock = new HashMap<>();
        int villagers = 1;
        Order refuse;

        @Override public int stockOf(ResourceLocation item, Order.CountScope scope) {
            return stock.getOrDefault(item, 0);
        }

        @Override public int stockOfTag(ResourceLocation tagId, Order.CountScope scope) {
            return tagStock.getOrDefault(tagId, 0);
        }

        @Override public int villagerCount() { return villagers; }

        @Override public boolean mayWork(Order order) { return order != refuse; }
    }

    private static ProducerRecipe recipe(ResourceLocation output) {
        return new ProducerRecipe() {
            @Override public ResourceLocation id() { return output; }
            @Override public ResourceLocation output() { return output; }
            @Override public int outputCount() { return 1; }
            @Override public int cookTimeTicks() { return 100; }
            @Override public int tier() { return 0; }
            @Override public List<? extends ResolvedIngredient> inputs() { return List.of(); }
        };
    }

    private static ResourceLocation id(String raw) {
        //? if >=1.21 {
        return ResourceLocation.parse(raw);
        //?} else {
        /*return new ResourceLocation(raw);
        *///?}
    }
}
