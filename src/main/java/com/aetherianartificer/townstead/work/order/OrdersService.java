package com.aetherianartificer.townstead.work.order;

import com.aetherianartificer.townstead.work.order.net.OrdersSnapshotS2CPayload;
import com.aetherianartificer.townstead.work.order.net.OrdersSnapshotS2CPayload.Option;
import com.aetherianartificer.townstead.work.order.net.OrdersSnapshotS2CPayload.Row;
import com.aetherianartificer.townstead.work.order.net.OrderEditC2SPayload;
import com.aetherianartificer.townstead.work.site.Worksite;
import com.aetherianartificer.townstead.work.site.WorksiteNames;
import com.aetherianartificer.townstead.work.site.WorksiteRegister;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The server side of the orders screen: turn a worksite into something the screen can draw, and
 * apply an edit that came back.
 *
 * <p>Kept apart from both the screen and the packets so the rules have one home. A client may ask
 * for anything; what actually happens is decided here.</p>
 */
public final class OrdersService {

    private OrdersService() {}

    // ── Snapshot ──

    /**
     * Everything the screen shows for one worksite. {@code options} is what this place could be
     * asked to make; supplying it is the caller's job because only the work engine knows.
     */
    public static OrdersSnapshotS2CPayload snapshot(ServerLevel level, Worksite site,
                                                    OrderContext context, List<Option> options,
                                                    List<OrdersSnapshotS2CPayload.Station> stations) {
        OrderList orders = site.orders();
        List<Row> rows = new ArrayList<>(orders.size());
        for (Order order : orders.orders()) {
            rows.add(row(order, context));
        }
        return new OrdersSnapshotS2CPayload(
                site.id(),
                WorksiteNames.display(site),
                detailOf(site),
                orders.listOnly(),
                List.copyOf(rows),
                List.copyOf(options),
                List.copyOf(stations));
    }

    private static Row row(Order order, OrderContext context) {
        int want = order.mode() == Order.Mode.PER_VILLAGER
                ? order.target() * Math.max(0, context.villagerCount())
                : order.target();
        int have = order.mode().countsProduction()
                ? order.produced()
                : context.stockOf(order.output(), order.scope());

        OrdersSnapshotS2CPayload.Status status;
        String reason = "";
        if (order.paused()) {
            status = OrdersSnapshotS2CPayload.Status.PAUSED;
        } else if (!order.wantsWork(context)) {
            status = OrdersSnapshotS2CPayload.Status.SATISFIED;
        } else if (order.inProgress() > 0) {
            status = OrdersSnapshotS2CPayload.Status.WORKING;
        } else if (!context.mayWork(order)) {
            status = OrdersSnapshotS2CPayload.Status.BLOCKED;
            reason = "Nobody working here is allowed to take this.";
        } else {
            status = OrdersSnapshotS2CPayload.Status.WAITING;
        }

        return new Row(order.output(), order.mode(), order.target(), order.scope(), order.paused(),
                modeLabel(order), scopeLabel(order), whoLabel(order),
                have, want, order.inProgress(), status, reason);
    }

    static String modeLabel(Order order) {
        return switch (order.mode()) {
            case MAKE -> "make " + order.target();
            case KEEP_STOCKED -> "keep " + order.target() + " in stock";
            case PER_VILLAGER -> order.target() + " per villager";
            case STANDING -> "standing";
        };
    }

    static String scopeLabel(Order order) {
        if (!order.mode().hasTarget()) return "";
        return order.scope() == Order.CountScope.VILLAGE ? "village" : "here";
    }

    static String whoLabel(Order order) {
        if (order.villager() != null) return "one villager";
        StringBuilder out = new StringBuilder();
        if (order.profession() != null) out.append(order.profession().getPath());
        if (order.minRank() > 0) {
            if (out.length() > 0) out.append(' ');
            out.append("rank ").append(order.minRank()).append('+');
        }
        return out.length() == 0 ? "anyone" : out.toString();
    }

    /**
     * The one-line "what kind of place is this" under the name. A binding id is a developer's
     * answer; a player wants to know whether they are looking at a room or a post.
     */
    private static String detailOf(Worksite site) {
        return com.aetherianartificer.townstead.work.site.WorksiteBindings.ANCHOR
                .equals(site.key().binding()) ? "post" : "room";
    }

    // ── Edits ──

    /**
     * Applies one edit, or refuses it. Every index, mode and amount is re-checked here: a client
     * describes what a player clicked, it does not decide what is true.
     */
    public static boolean apply(ServerLevel level, OrderEditC2SPayload edit) {
        WorksiteRegister register = WorksiteRegister.get(level.getServer());
        Worksite site = register.byId(edit.worksiteId());
        if (site == null) return false;
        OrderList orders = site.orders();

        boolean changed = switch (edit.action()) {
            case ADD -> add(orders, edit.value());
            case COPY -> copy(orders, edit.index());
            case REMOVE -> {
                Order order = orders.at(edit.index());
                yield order != null && orders.remove(order);
            }
            case MOVE -> orders.move(edit.index(), edit.amount());
            case SET_MODE -> {
                Order order = orders.at(edit.index());
                if (order == null) yield false;
                order.setMode(Order.Mode.parse(edit.value()));
                yield true;
            }
            case SET_TARGET -> {
                Order order = orders.at(edit.index());
                if (order == null) yield false;
                // Clamped rather than refused: a stuck stepper is worse than a capped number.
                order.setTarget(Math.min(Math.max(0, edit.amount()), MAX_TARGET));
                yield true;
            }
            case SET_SCOPE -> {
                Order order = orders.at(edit.index());
                if (order == null) yield false;
                order.setScope(Order.CountScope.parse(edit.value()));
                yield true;
            }
            case TOGGLE_PAUSE -> {
                Order order = orders.at(edit.index());
                if (order == null) yield false;
                order.setPaused(!order.paused());
                yield true;
            }
            case SET_LIST_ONLY -> {
                orders.setListOnly(edit.amount() != 0);
                yield true;
            }
            case RENAME -> {
                String cleaned = WorksiteNames.sanitise(edit.value());
                if (cleaned == null) yield false;
                site.setName(cleaned);
                yield true;
            }
        };
        if (changed) register.setDirty();
        return changed;
    }

    /** Beyond this a target is a typo, not an intention. */
    public static final int MAX_TARGET = 9999;

    /**
     * Appends a line. Several lines may name the same item, deliberately: "make 5 cakes" near the
     * top and "keep 20 cakes" at the bottom is a real thing to want, and so is one urgent batch
     * ahead of a standing one. They do not fight — a counted-production line tracks its own total,
     * and two stock-reading lines read the same shelf and go quiet together, with position deciding
     * which is worked first.
     */
    private static boolean add(OrderList orders, @Nullable String itemId) {
        ResourceLocation id = itemId == null || itemId.isBlank() ? null : tryParse(itemId);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return false;
        orders.add(new Order(id, Order.Mode.KEEP_STOCKED, 10));
        return true;
    }

    /** A line's twin, dropped in beneath it, carrying every setting but none of its progress. */
    private static boolean copy(OrderList orders, int index) {
        Order source = orders.at(index);
        if (source == null) return false;
        Order twin = new Order(source.output(), source.mode(), source.target());
        twin.setScope(source.scope());
        twin.setProfession(source.profession());
        twin.setMinRank(source.minRank());
        twin.setVillager(source.villager());
        // Not copied: produced and inProgress. A copy is a fresh instruction, not a duplicate of
        // work already done — and paused is left off so the twin starts doing something.
        orders.add(twin);
        orders.move(orders.size() - 1, index + 1);
        return true;
    }

    @Nullable
    private static ResourceLocation tryParse(String raw) {
        //? if >=1.21 {
        return ResourceLocation.tryParse(raw);
        //?} else {
        /*try {
            return new ResourceLocation(raw);
        } catch (Exception e) {
            return null;
        }
        *///?}
    }
}
