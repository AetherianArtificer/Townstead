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
                : order.isTag()
                        ? context.stockOfTag(order.output(), order.scope())
                        : context.stockOf(order.output(), order.scope());

        OrdersSnapshotS2CPayload.Status status = statusOf(order, context);
        String reason = "";
        if (status == OrdersSnapshotS2CPayload.Status.BLOCKED) {
            reason = "Nobody working here is allowed to take this.";
        }

        return new Row(order.output(), order.mode(), order.target(), order.scope(), order.paused(),
                modeLabel(order), scopeLabel(order), whoLabel(order),
                have, want, order.inProgress(), status, reason,
                order.isActivity(), order.isTag(),
                order.isActivity()
                        ? com.aetherianartificer.townstead.work.WorkActivities.labelOf(order.output())
                        : order.isTag() ? categoryLabel(order.output())
                        : !order.workpieceName().isEmpty() ? "Copy " + order.workpieceName() : "");
    }

    /**
     * A fully claimed line has no unreserved work left, but it is not done: those items are still
     * physically in a station. Active claims therefore win over the arithmetic satisfied state.
     */
    static OrdersSnapshotS2CPayload.Status statusOf(Order order, OrderContext context) {
        if (order.paused()) return OrdersSnapshotS2CPayload.Status.PAUSED;
        if (order.inProgress() > 0) return OrdersSnapshotS2CPayload.Status.WORKING;
        if (!order.wantsWork(context)) return OrdersSnapshotS2CPayload.Status.SATISFIED;
        if (!context.mayWork(order)) return OrdersSnapshotS2CPayload.Status.BLOCKED;
        return OrdersSnapshotS2CPayload.Status.WAITING;
    }

    /** What a category is called on screen: the tag path, minus the orders/ shelf mark. */
    public static String categoryLabel(ResourceLocation tagId) {
        String path = tagId.getPath();
        if (path.startsWith(OrderTags.CATEGORY_PREFIX)) {
            path = path.substring(OrderTags.CATEGORY_PREFIX.length());
        }
        String words = path.replace('_', ' ').replace('/', ' ');
        return words.isEmpty() ? path
                : Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    static String modeLabel(Order order) {
        // A job is not a quantity. It is on the list or it is not, and held or not.
        if (order.isActivity()) return order.paused() ? "Held" : "When there is any";
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
        return apply(level, null, edit);
    }

    public static boolean apply(ServerLevel level,
                                @Nullable net.minecraft.server.level.ServerPlayer player,
                                OrderEditC2SPayload edit) {
        WorksiteRegister register = WorksiteRegister.get(level.getServer());
        Worksite site = register.byId(edit.worksiteId());
        if (site == null) return false;
        OrderList orders = site.orders();

        boolean changed = switch (edit.action()) {
            case ADD -> add(orders, edit.value());
            case COMMISSION -> commission(level, player, orders, edit);
            case COPY -> copy(orders, edit.index());
            case REMOVE -> {
                Order order = orders.at(edit.index());
                if (order == null) yield false;
                // An escrowed workpiece goes back to whoever is cancelling — never deleted
                // with the line. With no player to hand it to, the line stays.
                if (order.workpiece() != null) {
                    if (player == null) yield false;
                    net.minecraft.nbt.CompoundTag escrow = order.takeWorkpiece();
                    //? if >=1.21 {
                    net.minecraft.world.item.ItemStack held = net.minecraft.world.item.ItemStack
                            .parse(level.registryAccess(), escrow).orElse(
                                    net.minecraft.world.item.ItemStack.EMPTY);
                    //?} else {
                    /*net.minecraft.world.item.ItemStack held =
                            net.minecraft.world.item.ItemStack.of(escrow);
                    *///?}
                    if (!held.isEmpty()) player.getInventory().placeItemBackInInventory(held);
                }
                yield orders.remove(order);
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
            // Handled before it reaches here — it is a lifecycle notice, not an edit.
            case CLOSED -> false;
            case RENAME -> {
                String cleaned = WorksiteNames.sanitise(edit.value());
                if (cleaned == null) yield false;
                site.setName(cleaned);
                yield true;
            }
        };
        if (changed) {
            register.setDirty();
            register.invalidateActivityScan();
            // Anyone watching this list is told on the next tick rather than polling for it.
            site.bumpOrdersRevision();
        }
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
        if (itemId == null || itemId.isBlank()) return false;
        // A "#" marks a tag line. Verified against the loaded tags, not just parsed: an order for
        // a set with nothing in it would be a line no candidate can ever satisfy.
        if (itemId.startsWith("#")) {
            ResourceLocation tagId = tryParse(itemId.substring(1));
            if (tagId == null || OrderTags.members(tagId).isEmpty()) return false;
            orders.add(new Order(tagId, Order.Kind.TAG, Order.Mode.KEEP_STOCKED, 10));
            return true;
        }
        ResourceLocation id = tryParse(itemId);
        if (id == null) return false;
        // A job and a thing arrive through the same door, and which one it is is not the client's
        // to assert: the server recognises a declared job by its id, and everything else has to be
        // a registered item or it is not added at all.
        if (com.aetherianartificer.townstead.work.WorkActivities.isKnown(id)) {
            orders.add(new Order(id, Order.Kind.ACTIVITY, Order.Mode.STANDING, 0));
            return true;
        }
        if (!BuiltInRegistries.ITEM.containsKey(id)) return false;
        orders.add(new Order(id, Order.Mode.KEEP_STOCKED, 10));
        return true;
    }

    /**
     * A commission: escrow the player's real stack into a make-N line. The item never crosses
     * the wire — the client names a slot, the server takes what actually sits in it, and only
     * after the stack proves to be the copy-source some station here declares.
     */
    private static boolean commission(ServerLevel level,
                                      @Nullable net.minecraft.server.level.ServerPlayer player,
                                      OrderList orders, OrderEditC2SPayload edit) {
        if (player == null) return false;
        ResourceLocation output = tryParse(edit.value());
        if (output == null || !BuiltInRegistries.ITEM.containsKey(output)) return false;
        int slot = edit.index();
        if (slot < 0 || slot >= player.getInventory().getContainerSize()) return false;
        net.minecraft.world.item.ItemStack stack = player.getInventory().getItem(slot);
        if (stack.isEmpty()) return false;
        ResourceLocation source = copySourceFor(output);
        if (source == null || !source.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))) {
            return false;
        }
        //? if >=1.21 {
        net.minecraft.nbt.CompoundTag tag =
                (net.minecraft.nbt.CompoundTag) stack.save(level.registryAccess());
        //?} else {
        /*net.minecraft.nbt.CompoundTag tag = stack.save(new net.minecraft.nbt.CompoundTag());
        *///?}
        Order order = new Order(output, Order.Mode.MAKE,
                Math.min(Math.max(1, edit.amount()), MAX_TARGET));
        order.setWorkpiece(tag, stack.getHoverName().getString());
        player.getInventory().setItem(slot, net.minecraft.world.item.ItemStack.EMPTY);
        orders.add(order);
        return true;
    }

    /** The input a duplicating produce line copies for this output, from any loaded def. */
    @Nullable
    private static ResourceLocation copySourceFor(ResourceLocation output) {
        for (var def : com.aetherianartificer.townstead.work.station.Workstations.all()) {
            for (var produce : def.produces()) {
                if (produce.copies() != null && output.equals(produce.output())) {
                    return produce.copies();
                }
            }
        }
        return null;
    }

    /** A line's twin, dropped in beneath it, carrying every setting but none of its progress. */
    private static boolean copy(OrderList orders, int index) {
        Order source = orders.at(index);
        if (source == null) return false;
        // Kind travels with the copy — dropping it turned a copied job or category into an item
        // line named after an id that is not an item.
        Order twin = new Order(source.output(), source.kind(), source.mode(), source.target());
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
