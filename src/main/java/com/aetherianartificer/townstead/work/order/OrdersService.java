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
import java.util.Set;

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
        List<OrdersSnapshotS2CPayload.Worker> workers = workerChoices(level, site);
        OrdersSnapshotS2CPayload.DriverControl drivers = driverControl(level, site);
        List<Row> rows = new ArrayList<>(orders.size());
        for (Order order : orders.orders()) {
            rows.add(row(level, site, order, context, options, stations));
        }
        return new OrdersSnapshotS2CPayload(
                site.id(),
                WorksiteNames.display(site),
                detailOf(site),
                orders.listOnly(),
                List.copyOf(rows),
                List.copyOf(options),
                List.copyOf(stations),
                workers,
                drivers);
    }

    private static List<OrdersSnapshotS2CPayload.Worker> workerChoices(
            ServerLevel level, Worksite site) {
        return workerChoices(level, site, null, List.of());
    }

    /**
     * Workers capable of this line, rather than everybody attached to the building. A Kitchen can
     * host both Cooks and Bakers without offering a Baker for a pot's cooked-meat order.
     */
    private static List<OrdersSnapshotS2CPayload.Worker> workerChoices(
            ServerLevel level, Worksite site, @Nullable Order order, List<Option> options) {
        List<OrdersSnapshotS2CPayload.Worker> out = new ArrayList<>();
        java.util.LinkedHashMap<ResourceLocation, String> professions = new java.util.LinkedHashMap<>();
        for (var def : com.aetherianartificer.townstead.work.site.WorksiteWork.professionsAt(
                level, site, com.aetherianartificer.townstead.work.site.Worksites.extentOf(level, site))) {
            if (order != null && !professionCanFulfil(def, order, options)) continue;
            professions.put(def.id(), def.displayName().getString());
            out.add(new OrdersSnapshotS2CPayload.Worker(
                    "profession:" + def.id(), def.displayName().getString(), "Profession"));
        }
        if (site.villageId() != Worksite.NO_VILLAGE) {
            for (net.conczin.mca.server.world.data.Village village
                    : net.conczin.mca.server.world.data.VillageManager.get(level)) {
                if (village.getId() != site.villageId()) continue;
                net.conczin.mca.server.world.data.Building building =
                        com.aetherianartificer.townstead.compat.mca.McaRoomBinding
                                .byId(level, site.key());
                String buildingType = building == null ? null
                        : com.aetherianartificer.townstead.compat.mca.McaBuildingCompat
                        .effectiveType(village, building);
                for (net.conczin.mca.entity.VillagerEntityMCA resident : village.getResidents(level)) {
                    if (buildingType != null
                            && com.aetherianartificer.townstead.work.site.BuildingWorkforceIndex
                            .defines(buildingType)
                            && !com.aetherianartificer.townstead.work.site.BuildingWorkforceIndex
                            .accepts(buildingType, resident)) continue;
                    ResourceLocation profession = BuiltInRegistries.VILLAGER_PROFESSION
                            .getKey(resident.getVillagerData().getProfession());
                    profession = com.aetherianartificer.townstead.profession.def.ProfessionDefs
                            .canonicalId(profession);
                    if (!professions.containsKey(profession)) continue;
                    out.add(new OrdersSnapshotS2CPayload.Worker(
                            "villager:" + resident.getUUID(), resident.getName().getString(),
                            professions.get(profession)));
                }
                break;
            }
        }
        return List.copyOf(out);
    }

    /** Assignment uses the same data-authored station and output filters as production. */
    static boolean professionCanFulfil(
            com.aetherianartificer.townstead.profession.def.ProfessionDef profession,
            Order order, List<Option> options) {
        if (profession == null || order == null) return false;
        if (order.isActivity()) return true;
        for (Option option : options) {
            if (option == null || option.activity() || option.tag()) continue;
            boolean requested = order.isTag()
                    ? OrderTags.contains(order.output(), option.output())
                    : order.product().equals(option.product());
            if (!requested) continue;
            for (var task : profession.workTasks()) {
                if (!task.allowsBlock(option.stationIcon())) continue;
                if (task.allowsRecipe(null, option.output())) return true;
            }
        }
        return false;
    }

    private static OrdersSnapshotS2CPayload.DriverControl driverControl(
            ServerLevel level, Worksite site) {
        if (!com.aetherianartificer.townstead.work.site.WorksiteDrivers
                .supportsDrivers(level, site)) {
            return OrdersSnapshotS2CPayload.DriverControl.none();
        }
        List<OrdersSnapshotS2CPayload.Driver> choices = new ArrayList<>();
        for (var candidate : com.aetherianartificer.townstead.work.site.WorksiteDrivers
                .candidates(level, site)) {
            choices.add(new OrdersSnapshotS2CPayload.Driver(
                    candidate.uuid().toString(), candidate.name(), candidate.type()));
        }
        return new OrdersSnapshotS2CPayload.DriverControl(true,
                com.aetherianartificer.townstead.work.site.WorksiteDrivers
                        .supportsWorkerFallback(level, site),
                site.driver() == null ? "" : site.driver().toString(), choices);
    }

    private static Row row(ServerLevel level, Worksite site, Order order, OrderContext context,
                           List<Option> options,
                           List<OrdersSnapshotS2CPayload.Station> stations) {
        int want = order.mode() == Order.Mode.PER_VILLAGER
                ? order.target() * Math.max(0, context.villagerCount())
                : order.target();
        int have = order.mode().countsProduction()
                ? order.produced()
                : context.stockOf(order, order.scope());

        OrdersSnapshotS2CPayload.Status status = statusOf(order, context);
        String reason = "";
        if (status == OrdersSnapshotS2CPayload.Status.BLOCKED) {
            reason = "Nobody working here is allowed to take this.";
        } else if (status == OrdersSnapshotS2CPayload.Status.WAITING && !order.isActivity()) {
            Option option = optionFor(order.product(), options);
            if (option == null) {
                // An option can disappear because the profession's recipe declaration does not
                // admit it, even while its physical station is plainly standing in the room.
                // Calling that a missing station sent players looking for a block they already
                // had (most visibly the Cafe skillet). Keep the diagnosis as specific as the
                // evidence actually permits.
                Set<net.minecraft.resources.ResourceLocation> worked =
                        com.aetherianartificer.townstead.work.site.WorksiteWork.typesAt(
                                level, site,
                                com.aetherianartificer.townstead.work.site.Worksites.extentOf(
                                        level, site));
                if (worked.isEmpty()) {
                    reason = "Nobody working here can produce this.";
                } else if (isBlockInteractionOutput(order.product())) {
                    reason = "No available job here can produce this.";
                } else {
                    boolean hasInstalledStation = stations != null && stations.stream()
                            .anyMatch(OrdersSnapshotS2CPayload.Station::present);
                    reason = hasInstalledStation
                            ? "No available recipe here can make this."
                            : "No installed station here can make this.";
                }
            } else if (!option.missing().isEmpty()) {
                reason = StationCatalogs.describeMissing(option.missing());
            } else if (!option.available() && !option.blocker().isBlank()) {
                reason = option.blocker();
            }
        }

        Option option = optionFor(order.product(), options);
        boolean operated = option != null && option.operated();
        boolean workerFallback = operated
                && com.aetherianartificer.townstead.work.site.WorksiteDrivers
                        .supportsWorkerFallback(level, site, option.stationIcon());
        List<OrdersSnapshotS2CPayload.Driver> operators = operated
                ? operatorChoices(level, site, option.stationIcon())
                : List.of();
        List<OrdersSnapshotS2CPayload.Worker> workers = workerChoices(level, site, order, options);
        String worker = workerValue(order);
        String operator = order.operator() == null ? "" : order.operator().toString();
        return new Row(order.output(), order.mode(), order.target(), order.scope(), order.paused(),
                modeLabel(order), scopeLabel(order), whoLabel(order),
                have, want, order.inProgress(), status, reason,
                order.isActivity(), order.isTag(),
                order.isActivity()
                        ? com.aetherianartificer.townstead.work.WorkActivities.labelOf(order.output())
                        : order.isTag() ? categoryLabel(order.output())
                        : !order.workpieceName().isEmpty() ? "Copy " + order.workpieceName()
                        : order.productName(),
                worker, order.operation(), operator,
                workLabel(order, workers, operators), operated, workerFallback, workers, operators,
                order.product());
    }

    private static String workerValue(Order order) {
        if (order.villager() != null) return "villager:" + order.villager();
        return order.profession() == null ? "" : "profession:" + order.profession();
    }

    private static String workLabel(Order order,
                                    List<OrdersSnapshotS2CPayload.Worker> workers,
                                    List<OrdersSnapshotS2CPayload.Driver> operators) {
        String workerValue = workerValue(order);
        String workerName = "Automatic";
        for (var choice : workers) {
            if (choice.value().equals(workerValue)) { workerName = choice.name(); break; }
        }
        return switch (order.operation()) {
            case AUTOMATIC -> workerValue.isEmpty() ? "" : workerName;
            case WORKER -> workerName + " · operates it themselves";
            case ENTITY -> {
                String operator = "Unavailable operator";
                if (order.operator() != null) {
                    for (var choice : operators) {
                        if (choice.uuid().equals(order.operator().toString())) {
                            operator = choice.name();
                            break;
                        }
                    }
                }
                yield workerName + " · with " + operator;
            }
        };
    }

    private static List<OrdersSnapshotS2CPayload.Driver> operatorChoices(
            ServerLevel level, Worksite site, ResourceLocation stationBlock) {
        List<OrdersSnapshotS2CPayload.Driver> out = new ArrayList<>();
        for (var candidate : com.aetherianartificer.townstead.work.site.WorksiteDrivers
                .candidates(level, site, stationBlock)) {
            out.add(new OrdersSnapshotS2CPayload.Driver(
                    candidate.uuid().toString(), candidate.name(), candidate.type()));
        }
        return List.copyOf(out);
    }

    private static @Nullable Option optionFor(ResourceLocation product, List<Option> options) {
        if (product == null || options == null) return null;
        for (Option option : options) {
            if (option != null && product.equals(option.product())) return option;
        }
        return null;
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
        // The tag names who owns the policy; the player-facing category names what it contains.
        // Keep the stable datapack id so existing orders and extensions continue to resolve.
        if (path.equals("baker_goods")) return "Baked Goods";
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
            case ADD -> add(level, site, orders, edit.value());
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
            case SET_DRIVER -> setDriver(level, site, edit.value());
            case SET_WORKER -> setWorker(level, site, orders.at(edit.index()), edit.value());
            case SET_OPERATOR -> setOperator(level, site, orders.at(edit.index()), edit.value());
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

    private static boolean setDriver(ServerLevel level, Worksite site, String raw) {
        if (!com.aetherianartificer.townstead.work.site.WorksiteDrivers
                .supportsDrivers(level, site)) return false;
        if (raw == null || raw.isBlank()) {
            if (site.driver() == null) return false;
            site.setDriver(null);
            return true;
        }
        java.util.UUID wanted;
        try {
            wanted = java.util.UUID.fromString(raw);
        } catch (IllegalArgumentException invalid) {
            return false;
        }
        boolean offered = com.aetherianartificer.townstead.work.site.WorksiteDrivers
                .candidates(level, site).stream().anyMatch(candidate -> candidate.uuid().equals(wanted));
        if (offered) {
            for (Worksite other : WorksiteRegister.get(level.getServer()).all()) {
                if (other != site && wanted.equals(other.driver())) {
                    offered = false;
                    break;
                }
            }
        }
        if (!offered || wanted.equals(site.driver())) return false;
        site.setDriver(wanted);
        return true;
    }

    private static boolean setWorker(ServerLevel level, Worksite site, @Nullable Order order,
                                     @Nullable String raw) {
        if (order == null) return false;
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty() || "automatic".equalsIgnoreCase(value)) {
            if (order.profession() == null && order.villager() == null) return false;
            order.setProfession(null);
            order.setVillager(null);
            return true;
        }
        List<Option> options = WorksiteCatalogs.optionsFor(level, site);
        boolean offered = workerChoices(level, site, order, options).stream()
                .anyMatch(choice -> choice.value().equals(value));
        if (!offered) return false;
        if (value.startsWith("profession:")) {
            ResourceLocation id = tryParse(value.substring("profession:".length()));
            if (id == null || (id.equals(order.profession()) && order.villager() == null)) return false;
            order.setVillager(null);
            order.setProfession(id);
            return true;
        }
        if (value.startsWith("villager:")) {
            try {
                java.util.UUID id = java.util.UUID.fromString(value.substring("villager:".length()));
                if (id.equals(order.villager())) return false;
                order.setProfession(null);
                order.setVillager(id);
                return true;
            } catch (IllegalArgumentException invalid) {
                return false;
            }
        }
        return false;
    }

    private static boolean setOperator(ServerLevel level, Worksite site, @Nullable Order order,
                                       @Nullable String raw) {
        if (order == null) return false;
        Option option = optionFor(order.product(), WorksiteCatalogs.optionsFor(level, site));
        if (option == null || !option.operated()) return false;
        String value = raw == null ? "automatic" : raw.trim();
        if (value.isEmpty() || "automatic".equalsIgnoreCase(value)) {
            if (order.operation() == Order.Operation.AUTOMATIC) return false;
            order.setOperation(Order.Operation.AUTOMATIC);
            return true;
        }
        if ("worker".equalsIgnoreCase(value)) {
            if (!com.aetherianartificer.townstead.work.site.WorksiteDrivers
                    .supportsWorkerFallback(level, site, option.stationIcon())
                    || order.operation() == Order.Operation.WORKER) return false;
            order.setOperation(Order.Operation.WORKER);
            return true;
        }
        if (!value.startsWith("entity:")) return false;
        java.util.UUID wanted;
        try {
            wanted = java.util.UUID.fromString(value.substring("entity:".length()));
        } catch (IllegalArgumentException invalid) {
            return false;
        }
        boolean offered = com.aetherianartificer.townstead.work.site.WorksiteDrivers
                .candidates(level, site, option.stationIcon()).stream()
                .anyMatch(choice -> choice.uuid().equals(wanted));
        if (!offered || (order.operation() == Order.Operation.ENTITY
                && wanted.equals(order.operator()))) return false;
        order.setOperator(wanted);
        return true;
    }

    /**
     * Appends a line. Several lines may name the same item, deliberately: "make 5 cakes" near the
     * top and "keep 20 cakes" at the bottom is a real thing to want, and so is one urgent batch
     * ahead of a standing one. They do not fight — a counted-production line tracks its own total,
     * and two stock-reading lines read the same shelf and go quiet together, with position deciding
     * which is worked first.
     */
    private static boolean add(ServerLevel level, Worksite site, OrderList orders,
                               @Nullable String itemId) {
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
        // Exact products are admitted only when the live server catalogue offers them. Their
        // synthetic id is never trusted as an item id and never needs registry registration.
        for (Option option : WorksiteCatalogs.optionsFor(level, site)) {
            if (option.activity() || option.tag() || !id.equals(option.product())) continue;
            Order order = new Order(option.output(), Order.Mode.KEEP_STOCKED, 10);
            order.setProduct(option.product(), option.label());
            order.setScope(WorksiteCatalogs.defaultScopeFor(level, site, option.output()));
            orders.add(order);
            return true;
        }
        // A job and a thing arrive through the same door, and which one it is is not the client's
        // to assert: the server recognises a declared job by its id, and everything else has to be
        // a registered item or it is not added at all.
        if (com.aetherianartificer.townstead.work.WorkActivities.isKnown(id)) {
            orders.add(new Order(id, Order.Kind.ACTIVITY, Order.Mode.STANDING, 0));
            return true;
        }
        if (!BuiltInRegistries.ITEM.containsKey(id)) return false;
        Order order = new Order(id, Order.Mode.KEEP_STOCKED, 10);
        order.setScope(WorksiteCatalogs.defaultScopeFor(level, site, id));
        orders.add(order);
        return true;
    }

    /** Harvested outputs belong to data-authored block interactions, not recipe catalogues. */
    private static boolean isBlockInteractionOutput(ResourceLocation output) {
        if (output == null) return false;
        for (var job : com.aetherianartificer.townstead.work.job.WorkJobs.forType(
                com.aetherianartificer.townstead.work.job.WorkJobDef.BLOCK_INTERACTION)) {
            if (job.target() == null) continue;
            for (var interaction : job.target().interactions()) {
                if (interaction.outputIds().contains(output)) return true;
            }
        }
        return false;
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
        twin.setProduct(source.product(), source.productName());
        twin.setScope(source.scope());
        twin.setProfession(source.profession());
        twin.setMinRank(source.minRank());
        twin.setVillager(source.villager());
        twin.setOperation(source.operation());
        if (source.operator() != null) twin.setOperator(source.operator());
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
