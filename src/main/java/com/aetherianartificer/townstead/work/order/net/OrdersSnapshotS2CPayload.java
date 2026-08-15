package com.aetherianartificer.townstead.work.order.net;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.work.order.Order;

import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * A worksite's order list as the screen needs to see it: the lines, why each one is or is not being
 * worked, and everything this place could be asked to make.
 *
 * <p>Status is computed server-side and sent, rather than re-derived on the client. The client has
 * no idea what is in the village's chests or whether a station is free, and the whole point of the
 * status column is to answer "why is that not happening?" honestly.</p>
 */
//? if neoforge {
public record OrdersSnapshotS2CPayload(long worksiteId, String worksiteName, String worksiteDetail,
                                       boolean listOnly, List<Row> rows, List<Option> options,
                                       List<Station> stations, List<Worker> workers,
                                       DriverControl driver)
        implements CustomPacketPayload {
//?} else {
/*public record OrdersSnapshotS2CPayload(long worksiteId, String worksiteName, String worksiteDetail,
                                       boolean listOnly, List<Row> rows, List<Option> options,
                                       List<Station> stations, List<Worker> workers,
                                       DriverControl driver) {
*///?}

    /** Why a line is or is not being worked, in the words the screen shows. */
    public enum Status { WORKING, WAITING, BLOCKED, PAUSED, SATISFIED }

    /**
     * One line. {@code have}/{@code want} are already resolved for the mode, so the client renders
     * a bar without knowing whether the number came from a shelf or from work done.
     *
     * <p>The raw {@code mode}/{@code target}/{@code scope}/{@code paused} travel alongside the
     * labels because the detail strip's controls have to know what the line currently says before
     * they can offer the next value. They are what the buttons read; the labels are what the eye
     * reads, and the server still re-decides both.</p>
     */
    public record Row(ResourceLocation output, Order.Mode mode, int target, Order.CountScope scope,
                      boolean paused, String modeLabel, String scopeLabel, String whoLabel,
                      int have, int want, int inProgress, Status status, String reason,
                      boolean activity, boolean tag, String label,
                      String worker, Order.Operation operation, String operator,
                      String workLabel, boolean operated, boolean workerFallback,
                      List<Worker> workers, List<Driver> operators) {
        public Row {
            workers = workers == null ? List.of() : List.copyOf(workers);
            operators = operators == null ? List.of() : List.copyOf(operators);
        }
    }

    /**
     * One thing a recipe needs: a consumed ingredient/vessel/supply or reusable tool. Sent as an
     * id rather than a name so the screen can draw the thing itself.
     */
    public record Need(List<ResourceLocation> items, int count, String label) {
        public Need {
            items = items == null ? List.of() : List.copyOf(items);
            label = label == null ? "" : label;
        }

        public Need(ResourceLocation item, int count) {
            this(item == null ? List.of() : List.of(item), count, "");
        }

        public Need(List<ResourceLocation> items, int count) {
            this(items, count, "");
        }

        /** Compatibility convenience for singleton needs and non-rotating server prose. */
        public ResourceLocation item() {
            return items.isEmpty() ? ResourceLocation.tryParse("minecraft:air") : items.get(0);
        }
    }

    /**
     * Something this worksite could make. {@code available} answers "could it be made right now";
     * {@code needs} describes the recipe and {@code missing} is its live worksite shortfall.
     */
    public record Option(ResourceLocation output, String stationLabel, ResourceLocation stationIcon,
                         boolean available, String blocker, int makes, List<Need> needs,
                         List<Need> missing,
                         boolean activity, boolean tag, String label, boolean commission,
                         boolean operated) {

        /** Something this place can make. */
        public static Option item(ResourceLocation output, String stationLabel,
                                  ResourceLocation stationIcon, boolean available, String blocker,
                                  int makes, List<Need> needs, List<Need> missing) {
            return new Option(output, stationLabel, stationIcon, available, blocker, makes, needs,
                    missing,
                    false, false, "", false, false);
        }

        /**
         * A duplication service: ordering it means handing over the specific item to copy, so
         * the screen asks for a workpiece instead of adding a plain line.
         */
        public static Option commissioned(ResourceLocation output, String stationLabel,
                                          ResourceLocation stationIcon, boolean available,
                                          String blocker, int makes, List<Need> needs,
                                          List<Need> missing, String label) {
            return new Option(output, stationLabel, stationIcon, available, blocker, makes, needs,
                    missing,
                    false, false, label, true, false);
        }

        /** A job this place can be told to prefer. It makes nothing, so it counts nothing. */
        public static Option job(ResourceLocation id, String label, ResourceLocation icon) {
            return new Option(id, "Job", icon, true, "", 0, List.of(), List.of(),
                    true, false, label, false, false);
        }

        /** A set of things this place can make some of: "any cooked meat". */
        public static Option category(ResourceLocation tagId, String label, ResourceLocation icon,
                                      boolean available, String blocker, List<Need> missing) {
            return new Option(tagId, "Kind", icon, available, blocker, 1, List.of(), missing,
                    false, true, label, false, false);
        }

        public Option withOperated(boolean value) {
            return new Option(output, stationLabel, stationIcon, available, blocker, makes,
                    needs, missing, activity, tag, label, commission, value);
        }
    }

    /**
     * A kind of workstation, and whether this worksite has one. Absent stations travel too: what a
     * kitchen is missing is the reason half the catalogue is not offered, and that is worth showing.
     */
    public record Station(String label, ResourceLocation icon, boolean present) {}

    /** One work animal available to this village's entity-powered station. */
    public record Driver(String uuid, String name, ResourceLocation type) {}

    /** A profession or named villager which may own an order. */
    public record Worker(String value, String name, String detail) {}

    /** Empty selected UUID means the worksite chooses the nearest eligible animal automatically. */
    public record DriverControl(boolean supported, boolean workerFallback,
                                String selected, List<Driver> choices) {
        public DriverControl {
            selected = selected == null ? "" : selected;
            choices = choices == null ? List.of() : List.copyOf(choices);
        }

        public static DriverControl none() {
            return new DriverControl(false, false, "", List.of());
        }
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeLong(worksiteId);
        buf.writeUtf(worksiteName);
        buf.writeUtf(worksiteDetail);
        buf.writeBoolean(listOnly);
        buf.writeVarInt(rows.size());
        for (Row row : rows) {
            buf.writeResourceLocation(row.output());
            buf.writeEnum(row.mode());
            buf.writeVarInt(row.target());
            buf.writeEnum(row.scope());
            buf.writeBoolean(row.paused());
            buf.writeUtf(row.modeLabel());
            buf.writeUtf(row.scopeLabel());
            buf.writeUtf(row.whoLabel());
            buf.writeVarInt(row.have());
            buf.writeVarInt(row.want());
            buf.writeVarInt(row.inProgress());
            buf.writeEnum(row.status());
            buf.writeUtf(row.reason());
            buf.writeBoolean(row.activity());
            buf.writeBoolean(row.tag());
            buf.writeUtf(row.label());
            buf.writeUtf(row.worker());
            buf.writeEnum(row.operation());
            buf.writeUtf(row.operator());
            buf.writeUtf(row.workLabel());
            buf.writeBoolean(row.operated());
            buf.writeBoolean(row.workerFallback());
            buf.writeVarInt(row.workers().size());
            for (Worker worker : row.workers()) {
                buf.writeUtf(worker.value());
                buf.writeUtf(worker.name());
                buf.writeUtf(worker.detail());
            }
            buf.writeVarInt(row.operators().size());
            for (Driver operator : row.operators()) {
                buf.writeUtf(operator.uuid());
                buf.writeUtf(operator.name());
                buf.writeResourceLocation(operator.type());
            }
        }
        buf.writeVarInt(options.size());
        for (Option option : options) {
            buf.writeResourceLocation(option.output());
            buf.writeUtf(option.stationLabel());
            buf.writeResourceLocation(option.stationIcon());
            buf.writeBoolean(option.available());
            buf.writeUtf(option.blocker());
            buf.writeVarInt(option.makes());
            buf.writeVarInt(option.needs().size());
            for (Need need : option.needs()) {
                buf.writeVarInt(need.items().size());
                for (ResourceLocation item : need.items()) buf.writeResourceLocation(item);
                buf.writeVarInt(need.count());
                buf.writeUtf(need.label());
            }
            buf.writeVarInt(option.missing().size());
            for (Need need : option.missing()) {
                buf.writeVarInt(need.items().size());
                for (ResourceLocation item : need.items()) buf.writeResourceLocation(item);
                buf.writeVarInt(need.count());
                buf.writeUtf(need.label());
            }
            buf.writeBoolean(option.activity());
            buf.writeBoolean(option.tag());
            buf.writeUtf(option.label());
            buf.writeBoolean(option.commission());
            buf.writeBoolean(option.operated());
        }
        buf.writeVarInt(stations.size());
        for (Station station : stations) {
            buf.writeUtf(station.label());
            buf.writeResourceLocation(station.icon());
            buf.writeBoolean(station.present());
        }
        buf.writeVarInt(workers.size());
        for (Worker worker : workers) {
            buf.writeUtf(worker.value());
            buf.writeUtf(worker.name());
            buf.writeUtf(worker.detail());
        }
        buf.writeBoolean(driver.supported());
        buf.writeBoolean(driver.workerFallback());
        buf.writeUtf(driver.selected());
        buf.writeVarInt(driver.choices().size());
        for (Driver choice : driver.choices()) {
            buf.writeUtf(choice.uuid());
            buf.writeUtf(choice.name());
            buf.writeResourceLocation(choice.type());
        }
    }

    public static OrdersSnapshotS2CPayload read(FriendlyByteBuf buf) {
        long id = buf.readLong();
        String name = buf.readUtf();
        String detail = buf.readUtf();
        boolean listOnly = buf.readBoolean();

        int rowCount = buf.readVarInt();
        List<Row> rows = new ArrayList<>(rowCount);
        for (int i = 0; i < rowCount; i++) {
            ResourceLocation output = buf.readResourceLocation();
            Order.Mode mode = buf.readEnum(Order.Mode.class);
            int target = buf.readVarInt();
            Order.CountScope scope = buf.readEnum(Order.CountScope.class);
            boolean paused = buf.readBoolean();
            String modeLabel = buf.readUtf();
            String scopeLabel = buf.readUtf();
            String whoLabel = buf.readUtf();
            int have = buf.readVarInt();
            int want = buf.readVarInt();
            int inProgress = buf.readVarInt();
            Status status = buf.readEnum(Status.class);
            String reason = buf.readUtf();
            boolean activity = buf.readBoolean();
            boolean tag = buf.readBoolean();
            String label = buf.readUtf();
            String worker = buf.readUtf();
            Order.Operation operation = buf.readEnum(Order.Operation.class);
            String operator = buf.readUtf();
            String workLabel = buf.readUtf();
            boolean operated = buf.readBoolean();
            boolean workerFallback = buf.readBoolean();
            int workerCount = buf.readVarInt();
            List<Worker> rowWorkers = new ArrayList<>(workerCount);
            for (int n = 0; n < workerCount; n++) {
                rowWorkers.add(new Worker(buf.readUtf(), buf.readUtf(), buf.readUtf()));
            }
            int operatorCount = buf.readVarInt();
            List<Driver> operators = new ArrayList<>(operatorCount);
            for (int n = 0; n < operatorCount; n++) {
                operators.add(new Driver(buf.readUtf(), buf.readUtf(), buf.readResourceLocation()));
            }
            rows.add(new Row(output, mode, target, scope, paused,
                    modeLabel, scopeLabel, whoLabel, have, want, inProgress, status, reason,
                    activity, tag, label, worker, operation, operator, workLabel,
                    operated, workerFallback, rowWorkers, operators));
        }
        int optionCount = buf.readVarInt();
        List<Option> options = new ArrayList<>(optionCount);
        for (int i = 0; i < optionCount; i++) {
            ResourceLocation output = buf.readResourceLocation();
            String stationLabel = buf.readUtf();
            ResourceLocation stationIcon = buf.readResourceLocation();
            boolean available = buf.readBoolean();
            String blocker = buf.readUtf();
            int makes = buf.readVarInt();
            int needCount = buf.readVarInt();
            List<Need> needs = new ArrayList<>(needCount);
            for (int n = 0; n < needCount; n++) {
                int alternatives = buf.readVarInt();
                List<ResourceLocation> items = new ArrayList<>(alternatives);
                for (int a = 0; a < alternatives; a++) items.add(buf.readResourceLocation());
                needs.add(new Need(items, buf.readVarInt(), buf.readUtf()));
            }
            int missingCount = buf.readVarInt();
            List<Need> missing = new ArrayList<>(missingCount);
            for (int n = 0; n < missingCount; n++) {
                int alternatives = buf.readVarInt();
                List<ResourceLocation> items = new ArrayList<>(alternatives);
                for (int a = 0; a < alternatives; a++) items.add(buf.readResourceLocation());
                missing.add(new Need(items, buf.readVarInt(), buf.readUtf()));
            }
            boolean activity = buf.readBoolean();
            boolean tag = buf.readBoolean();
            String label = buf.readUtf();
            boolean commission = buf.readBoolean();
            boolean operated = buf.readBoolean();
            options.add(new Option(output, stationLabel, stationIcon, available, blocker,
                    makes, List.copyOf(needs), List.copyOf(missing),
                    activity, tag, label, commission, operated));
        }
        int stationCount = buf.readVarInt();
        List<Station> stations = new ArrayList<>(stationCount);
        for (int i = 0; i < stationCount; i++) {
            stations.add(new Station(buf.readUtf(), buf.readResourceLocation(), buf.readBoolean()));
        }
        int workerCount = buf.readVarInt();
        List<Worker> workers = new ArrayList<>(workerCount);
        for (int i = 0; i < workerCount; i++) {
            workers.add(new Worker(buf.readUtf(), buf.readUtf(), buf.readUtf()));
        }
        boolean driverSupported = buf.readBoolean();
        boolean workerFallback = buf.readBoolean();
        String selectedDriver = buf.readUtf();
        int driverCount = buf.readVarInt();
        List<Driver> drivers = new ArrayList<>(driverCount);
        for (int i = 0; i < driverCount; i++) {
            drivers.add(new Driver(buf.readUtf(), buf.readUtf(), buf.readResourceLocation()));
        }
        return new OrdersSnapshotS2CPayload(id, name, detail, listOnly,
                List.copyOf(rows), List.copyOf(options), List.copyOf(stations),
                List.copyOf(workers),
                new DriverControl(driverSupported, workerFallback, selectedDriver, drivers));
    }

    //? if neoforge {
    public static final Type<OrdersSnapshotS2CPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "orders_snapshot"));
    public static final StreamCodec<FriendlyByteBuf, OrdersSnapshotS2CPayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> payload.write(buf), OrdersSnapshotS2CPayload::read);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}
    //? if forge {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "orders_snapshot");
    *///?}
}
