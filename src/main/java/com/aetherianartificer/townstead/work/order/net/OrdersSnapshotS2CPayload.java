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
                                       List<Station> stations)
        implements CustomPacketPayload {
//?} else {
/*public record OrdersSnapshotS2CPayload(long worksiteId, String worksiteName, String worksiteDetail,
                                       boolean listOnly, List<Row> rows, List<Option> options,
                                       List<Station> stations) {
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
                      boolean activity, boolean tag, String label) {}

    /**
     * Something this worksite could make. {@code available} answers "could it be made right now" —
     * the inputs are on the shelves here — which is a different question from whether the station
     * exists, and the only one worth putting a filter on.
     */
    public record Option(ResourceLocation output, String stationLabel, ResourceLocation stationIcon,
                         boolean available, String blocker, int makes, List<String> needs,
                         boolean activity, boolean tag, String label) {

        /** Something this place can make. */
        public static Option item(ResourceLocation output, String stationLabel,
                                  ResourceLocation stationIcon, boolean available, String blocker,
                                  int makes, List<String> needs) {
            return new Option(output, stationLabel, stationIcon, available, blocker, makes, needs,
                    false, false, "");
        }

        /** A job this place can be told to prefer. It makes nothing, so it counts nothing. */
        public static Option job(ResourceLocation id, String label, ResourceLocation icon) {
            return new Option(id, "Job", icon, true, "", 0, List.of(), true, false, label);
        }

        /** A set of things this place can make some of: "any cooked meat". */
        public static Option category(ResourceLocation tagId, String label, ResourceLocation icon,
                                      boolean available, String blocker) {
            return new Option(tagId, "Kind", icon, available, blocker, 1, List.of(),
                    false, true, label);
        }
    }

    /**
     * A kind of workstation, and whether this worksite has one. Absent stations travel too: what a
     * kitchen is missing is the reason half the catalogue is not offered, and that is worth showing.
     */
    public record Station(String label, ResourceLocation icon, boolean present) {}

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
            for (String need : option.needs()) buf.writeUtf(need);
            buf.writeBoolean(option.activity());
            buf.writeBoolean(option.tag());
            buf.writeUtf(option.label());
        }
        buf.writeVarInt(stations.size());
        for (Station station : stations) {
            buf.writeUtf(station.label());
            buf.writeResourceLocation(station.icon());
            buf.writeBoolean(station.present());
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
            rows.add(new Row(buf.readResourceLocation(), buf.readEnum(Order.Mode.class),
                    buf.readVarInt(), buf.readEnum(Order.CountScope.class), buf.readBoolean(),
                    buf.readUtf(), buf.readUtf(), buf.readUtf(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readEnum(Status.class), buf.readUtf(),
                    buf.readBoolean(), buf.readBoolean(), buf.readUtf()));
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
            List<String> needs = new ArrayList<>(needCount);
            for (int n = 0; n < needCount; n++) needs.add(buf.readUtf());
            boolean activity = buf.readBoolean();
            boolean tag = buf.readBoolean();
            String label = buf.readUtf();
            options.add(new Option(output, stationLabel, stationIcon, available, blocker,
                    makes, List.copyOf(needs), activity, tag, label));
        }
        int stationCount = buf.readVarInt();
        List<Station> stations = new ArrayList<>(stationCount);
        for (int i = 0; i < stationCount; i++) {
            stations.add(new Station(buf.readUtf(), buf.readResourceLocation(), buf.readBoolean()));
        }
        return new OrdersSnapshotS2CPayload(id, name, detail, listOnly,
                List.copyOf(rows), List.copyOf(options), List.copyOf(stations));
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
