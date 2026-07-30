package com.aetherianartificer.townstead.work.order;

import net.minecraft.resources.ResourceLocation;

/**
 * What an order needs to know about the world to say whether it still wants work.
 *
 * <p>An interface rather than a {@code ServerLevel} so the decision logic stays pure: whether "keep
 * twenty in stock" is satisfied is arithmetic over a stock count and a head count, and arithmetic
 * should be testable without a world.</p>
 */
public interface OrderContext {

    /** How many of this item are stored within the given scope. */
    int stockOf(ResourceLocation item, Order.CountScope scope);

    /** Villagers the per-villager targets scale against. */
    int villagerCount();

    /** Whether the worker being asked is allowed to take this line. */
    boolean mayWork(Order order);
}
