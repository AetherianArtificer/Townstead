package com.aetherianartificer.townstead.work.order;

import com.aetherianartificer.townstead.work.producer.ProducerRecipe;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A worksite's production orders, in the order they are worked.
 *
 * <p>One list per worksite — not one per villager, not one per board. Everyone working there works
 * from it, and "you specifically, Mira" is a field on a line rather than a second list, so there is
 * never a precedence question between two sets of instructions.</p>
 *
 * <p>Position <em>is</em> the priority system. The first line that still wants work and that this
 * worker can actually do wins; there are no weights and no scores, because a player who drags pies
 * to the top has already said what they meant.</p>
 */
public final class OrderList {

    private final List<Order> orders = new ArrayList<>();

    /** When true, a worker with nothing on the list to do stands down instead of choosing freely. */
    private boolean listOnly;

    public List<Order> orders() {
        return List.copyOf(orders);
    }

    public int size() {
        return orders.size();
    }

    public boolean isEmpty() {
        return orders.isEmpty();
    }

    public boolean listOnly() {
        return listOnly;
    }

    public void setListOnly(boolean value) {
        this.listOnly = value;
    }

    public void add(Order order) {
        if (order != null) orders.add(order);
    }

    public boolean remove(Order order) {
        return orders.remove(order);
    }

    /** Moves a line to a new position, which is the only priority control there is. */
    public boolean move(int from, int to) {
        if (from < 0 || from >= orders.size() || to < 0 || to >= orders.size() || from == to) return false;
        orders.add(to, orders.remove(from));
        return true;
    }

    @Nullable
    public Order at(int index) {
        return index < 0 || index >= orders.size() ? null : orders.get(index);
    }

    /** Drops every line that has finished for good, so a completed batch stops taking up space. */
    public int sweepRetired() {
        int before = orders.size();
        orders.removeIf(Order::retired);
        return before - orders.size();
    }

    // ── Selection ──

    /**
     * A line and the recipe that satisfies it.
     *
     * <p>Both travel together because the list may hold <em>several lines for the same item</em> —
     * "make 5 cakes" urgently near the top and "keep 20 cakes" quietly at the bottom is a sensible
     * thing to ask for. Handing back only the recipe would force the caller to look the line up
     * again by output, and it would find the first one rather than the one that was chosen.</p>
     */
    public record Pick(Order order, ProducerRecipe recipe) {}

    /**
     * The first ordered line this worker can actually work right now, or null when none applies.
     *
     * <p>Candidates are whatever the engine had already decided was possible — station supported,
     * ingredients reachable — so this only ever narrows. Returning null means "nothing ordered
     * applies", which is the caller's cue to fall through to its own choice unless the list is
     * marked as the only work allowed.</p>
     */
    @Nullable
    public Pick firstWorkable(List<? extends ProducerRecipe> candidates, OrderContext context) {
        if (orders.isEmpty() || candidates == null || candidates.isEmpty()) return null;

        // Indexed by output because an order names an item: any recipe making bread will do.
        Map<ResourceLocation, ProducerRecipe> byOutput = new HashMap<>(candidates.size());
        for (ProducerRecipe candidate : candidates) {
            if (candidate == null || candidate.output() == null) continue;
            byOutput.putIfAbsent(candidate.output(), candidate);
        }
        if (byOutput.isEmpty()) return null;

        for (Order order : orders) {
            if (!order.wantsWork(context)) continue;
            ProducerRecipe match = byOutput.get(order.output());
            if (match == null) continue;
            // Eligibility is asked last: it is the most expensive question and the rarest filter.
            if (!context.mayWork(order)) continue;
            return new Pick(order, match);
        }
        return null;
    }
}
