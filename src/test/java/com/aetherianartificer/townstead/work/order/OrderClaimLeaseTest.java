package com.aetherianartificer.townstead.work.order;

import com.aetherianartificer.townstead.work.order.net.OrdersSnapshotS2CPayload;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderClaimLeaseTest {
    private static final ResourceLocation COFFEE = ResourceLocation.tryParse("minecraft:cookie");

    @Test
    void aClaimNobodyRenewsFreesTheLine() {
        Order order = new Order(COFFEE, Order.Mode.MAKE, 1);
        order.claim(1, 600L);

        assertEquals(0, order.outstanding(new At(599L)), "still spoken for while the lease runs");
        assertEquals(OrdersSnapshotS2CPayload.Status.WORKING, OrdersService.statusOf(order, new At(599L)));

        assertEquals(1, order.outstanding(new At(600L)), "a vanished worker must not hold the line");
        assertEquals(0, order.inProgress());
        assertEquals(OrdersSnapshotS2CPayload.Status.WAITING, OrdersService.statusOf(order, new At(600L)));
    }

    @Test
    void renewingKeepsTheClaim() {
        Order order = new Order(COFFEE, Order.Mode.MAKE, 1);
        UUID lease = order.claim(1, 600L);
        order.renew(lease, 1200L);

        assertEquals(0, order.outstanding(new At(1000L)));
        assertEquals(1, order.inProgress());
    }

    @Test
    void aLapsedClaimStaysLapsedAndItsWorkStillCounts() {
        Order order = new Order(COFFEE, Order.Mode.MAKE, 2);
        UUID lease = order.claim(2, 600L);
        order.expireClaims(600L);

        order.renew(lease, 5000L);
        assertEquals(2, order.outstanding(new At(700L)), "renewing cannot revive a claim another worker may have replaced");

        order.finish(lease, 2);
        assertEquals(0, order.inProgress(), "settling a lapsed claim must not take from anyone else's");
        assertEquals(2, order.produced(), "what physically appeared is still credited");
    }

    @Test
    void settlingAClaimOnlyTakesItsOwnAmount() {
        Order order = new Order(COFFEE, Order.Mode.MAKE, 10);
        UUID first = order.claim(3, 600L);
        order.claim(4, 600L);

        order.abandon(first);
        assertEquals(4, order.inProgress());
        order.abandon(first);
        assertEquals(4, order.inProgress(), "settling twice must not eat the other worker's claim");
    }

    @Test
    void aContextWithoutAClockNeverExpiresAnything() {
        Order order = new Order(COFFEE, Order.Mode.MAKE, 1);
        order.claim(1, 600L);
        assertEquals(0, order.outstanding(new OrderContext() {
            @Override public int stockOf(ResourceLocation item, Order.CountScope scope) { return 0; }
            @Override public int stockOfTag(ResourceLocation tagId, Order.CountScope scope) { return 0; }
            @Override public int villagerCount() { return 0; }
            @Override public boolean mayWork(Order candidate) { return true; }
        }));
    }

    private record At(long gameTime) implements OrderContext {
        @Override public int stockOf(ResourceLocation item, Order.CountScope scope) { return 0; }
        @Override public int stockOfTag(ResourceLocation tagId, Order.CountScope scope) { return 0; }
        @Override public int villagerCount() { return 0; }
        @Override public boolean mayWork(Order order) { return true; }
    }
}
