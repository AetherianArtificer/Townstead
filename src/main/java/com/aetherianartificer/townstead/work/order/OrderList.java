package com.aetherianartificer.townstead.work.order;

import com.aetherianartificer.townstead.work.producer.ProducerRecipe;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

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

    /**
     * Whether the sheet has taken authority over this output. A satisfied or paused line still
     * governs its item: falling through to autonomous production would immediately violate the
     * quantity the player just set. Removing the line hands that item back to autonomy.
     */
    public boolean governs(@Nullable ResourceLocation output) {
        if (output == null) return false;
        for (Order order : orders) {
            if (order.matches(output)) return true;
        }
        return false;
    }

    /** Exact recipe counterpart used by component-bearing products. */
    public boolean governs(@Nullable ProducerRecipe recipe) {
        if (recipe == null) return false;
        for (Order order : orders) {
            if (order.matches(recipe)) return true;
        }
        return false;
    }

    /** Position of the first active line governing this output, or MAX when it is disallowed. */
    public int priority(@Nullable ResourceLocation output, OrderContext context) {
        if (output == null) return Integer.MAX_VALUE;
        boolean governed = false;
        for (int index = 0; index < orders.size(); index++) {
            Order order = orders.get(index);
            if (!order.matches(output)) continue;
            governed = true;
            if (order.wantsWork(context) && context.mayWork(order)) return index;
        }
        if (governed || listOnly) return Integer.MAX_VALUE;
        return orders.size();
    }

    /**
     * Recipe-aware station priority. Component-bearing products can share an item id while still
     * being different things to an order (a blank raw pizza and a prepared raw pizza, or two
     * potion contents). Passing only {@link ProducerRecipe#output()} through the item overload
     * deliberately cannot match those lines; station selection must retain the recipe identity.
     */
    public int priority(@Nullable ProducerRecipe candidate, OrderContext context) {
        if (candidate == null || candidate.output() == null) return Integer.MAX_VALUE;
        boolean governed = false;
        for (int index = 0; index < orders.size(); index++) {
            Order order = orders.get(index);
            if (!order.matches(candidate)) continue;
            governed = true;
            if (order.wantsWork(context) && context.mayWork(order)) return index;
        }
        if (governed || listOnly) return Integer.MAX_VALUE;
        return orders.size();
    }

    /**
     * Takes over another list's lines, keeping this one authoritative, and reports how many moved.
     *
     * <p>Used when two records turn out to be the same place. A line is only taken when nothing
     * here already orders that item: several lines for one item are legal on purpose, but two of
     * them arriving from a merge is an accident rather than an instruction.</p>
     */
    public int absorb(@Nullable OrderList other) {
        if (other == null || other == this || other.orders.isEmpty()) return 0;
        java.util.Set<ResourceLocation> mine = new java.util.HashSet<>();
        for (Order order : orders) mine.add(order.product());
        int moved = 0;
        for (Order order : other.orders) {
            if (!mine.add(order.product())) continue;
            orders.add(order);
            moved++;
        }
        other.orders.clear();
        return moved;
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
     * A line and a non-recipe operation which can produce something that satisfies it.
     *
     * <p>Harvesting and other player-like interactions may have several possible outcomes for
     * one world target.  Keeping the matched output beside the candidate is important when an
     * operation declares more than one result: the caller must credit the item the order actually
     * asked for, rather than whichever result happened to be listed first.</p>
     */
    public record OutputPick<T>(Order order, T candidate, ResourceLocation output) {}

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

        // Indexed by exact product: ordinary recipes still key to their item id, while potions
        // remain distinct by registered contents and bottle form.
        Map<ResourceLocation, ProducerRecipe> byOutput = new HashMap<>(candidates.size());
        for (ProducerRecipe candidate : candidates) {
            if (candidate == null || candidate.output() == null) continue;
            byOutput.putIfAbsent(OrderProducts.key(candidate), candidate);
        }
        if (byOutput.isEmpty()) return null;

        for (Order order : orders) {
            if (!order.wantsWork(context)) continue;
            // An item line looks its output up; a tag line walks the candidates for a member.
            ProducerRecipe match = order.isTag()
                    ? firstMatching(candidates, order)
                    : byOutput.get(order.product());
            if (match == null) continue;
            // Eligibility is asked last: it is the most expensive question and the rarest filter.
            if (!context.mayWork(order)) continue;
            return new Pick(order, match);
        }
        return null;
    }

    /**
     * The first ordered line a general output-producing operation can work.
     *
     * <p>This is the recipe-independent counterpart to {@link #firstWorkable(List, OrderContext)}.
     * The list still owns priority and eligibility; the caller supplies each operation's declared
     * outputs and the live check which says whether that operation can start.  The live check is
     * deliberately made only after a line and output match, so an unavailable lower choice does
     * not gather supplies or reserve anything while a higher order is being considered.</p>
     */
    @Nullable
    public <T> OutputPick<T> firstWorkableOutputs(
            List<T> candidates,
            Function<T, ? extends Collection<ResourceLocation>> outputsOf,
            Predicate<T> workable,
            OrderContext context) {
        if (orders.isEmpty() || candidates == null || candidates.isEmpty()
                || outputsOf == null || workable == null || context == null) return null;

        for (Order order : orders) {
            if (!order.wantsWork(context) || !context.mayWork(order)) continue;
            for (T candidate : candidates) {
                if (candidate == null) continue;
                Collection<ResourceLocation> outputs = outputsOf.apply(candidate);
                if (outputs == null || outputs.isEmpty()) continue;
                for (ResourceLocation output : outputs) {
                    if (order.matches(output) && workable.test(candidate)) {
                        return new OutputPick<>(order, candidate, output);
                    }
                }
            }
        }
        return null;
    }

    @Nullable
    private static ProducerRecipe firstMatching(List<? extends ProducerRecipe> candidates, Order order) {
        for (ProducerRecipe candidate : candidates) {
            if (candidate != null && order.matches(candidate)) return candidate;
        }
        return null;
    }
}
