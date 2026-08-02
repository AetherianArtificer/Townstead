package com.aetherianartificer.townstead.work.order;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Per-item refinements on what counts as "in stock" for an order line.
 *
 * <p>Most items are their id: a bread is a bread. Some carry their state in data — a canteen is
 * purified or not on the same item id — and an order for the finished thing must not be satisfied
 * by the raw one, or a "keep 10 purified" line reads full while the shelf holds swamp water.
 * Compat that knows such an item registers the test here, and every place that counts stock asks
 * before adding a stack in.</p>
 *
 * <p>Counting only. Fetching, sourcing and recipe planning keep their own matchers: the same
 * canteen that must not <em>satisfy</em> a purification order is exactly what the work wants to
 * <em>take</em> as input.</p>
 */
public final class OrderStackFilters {

    private static final Map<ResourceLocation, Predicate<ItemStack>> FILTERS = new ConcurrentHashMap<>();

    private OrderStackFilters() {}

    /** Registers (or replaces) the test for one item id. */
    public static void register(ResourceLocation item, Predicate<ItemStack> filter) {
        if (item == null || filter == null) return;
        FILTERS.put(item, filter);
    }

    /** Whether this stack of that item satisfies an order line for it. No filter means yes. */
    public static boolean counts(ResourceLocation item, ItemStack stack) {
        Predicate<ItemStack> filter = FILTERS.get(item);
        return filter == null || filter.test(stack);
    }
}
