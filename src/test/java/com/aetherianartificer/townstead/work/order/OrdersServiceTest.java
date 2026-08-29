package com.aetherianartificer.townstead.work.order;

import com.aetherianartificer.townstead.work.order.net.OrdersSnapshotS2CPayload;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class OrdersServiceTest {

    private static final ResourceLocation COFFEE = ResourceLocation.tryParse("rusticdelight:coffee");

    @Test
    void bakerCategoryUsesTheProductNameWithoutChangingItsStableTagId() {
        assertEquals("Baked Goods", OrdersService.categoryLabel(
                ResourceLocation.tryParse("townstead:orders/baker_goods")));
    }

    @Test
    void fullyClaimedOrderRemainsWorkingUntilItsOutputIsDelivered() {
        Order order = new Order(COFFEE, Order.Mode.MAKE, 10);
        OrderContext context = new EmptyContext();

        order.claim(10);
        assertFalse(order.wantsWork(context), "all ten units are reserved, so no worker may claim more");
        assertEquals(OrdersSnapshotS2CPayload.Status.WORKING,
                OrdersService.statusOf(order, context));

        order.finish(10, 10);
        assertEquals(OrdersSnapshotS2CPayload.Status.SATISFIED,
                OrdersService.statusOf(order, context));
    }

    private static final class EmptyContext implements OrderContext {
        @Override public int stockOf(ResourceLocation item, Order.CountScope scope) { return 0; }
        @Override public int stockOfTag(ResourceLocation tagId, Order.CountScope scope) { return 0; }
        @Override public int villagerCount() { return 0; }
        @Override public boolean mayWork(Order order) { return true; }
    }
}
