package com.aetherianartificer.townstead.work.order.net;

import com.aetherianartificer.townstead.Townstead;

import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

/**
 * One edit to a worksite's order list, addressed by worksite id so a player editing a kitchen they
 * have since walked out of still edits that kitchen.
 *
 * <p>Every field the server needs travels in one shape rather than one payload per verb: the list
 * is small, edits are rare, and a single validated entry point is easier to keep honest than eight.
 * The server re-checks everything — an index, a mode, a target — because a client is only ever a
 * suggestion.</p>
 */
//? if neoforge {
public record OrderEditC2SPayload(long worksiteId, Action action, int index, int amount, String value)
        implements CustomPacketPayload {
//?} else {
/*public record OrderEditC2SPayload(long worksiteId, Action action, int index, int amount, String value) {
*///?}

    public enum Action {
        /** {@code value} names the item. Appended at the bottom, where a new line belongs. */
        ADD,
        /** Duplicates the line at {@code index}, settings and all, immediately beneath it. */
        COPY,
        REMOVE,
        /** {@code amount} is the destination index. Position is the whole priority system. */
        MOVE,
        /** {@code value} names an {@code Order.Mode}. */
        SET_MODE,
        SET_TARGET,
        /** {@code value} names an {@code Order.CountScope}. */
        SET_SCOPE,
        TOGGLE_PAUSE,
        /** {@code amount != 0} turns on "work this list only". */
        SET_LIST_ONLY,
        /** {@code value} is the new worksite name. */
        RENAME,
        /**
         * The screen closed, so stop pushing snapshots at it.
         *
         * <p>Rides this payload rather than getting its own because every edit already carries the
         * worksite id, which is the only thing the server needs to forget a watcher.</p>
         */
        CLOSED
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeLong(worksiteId);
        buf.writeEnum(action);
        buf.writeVarInt(index);
        buf.writeVarInt(amount);
        buf.writeUtf(value);
    }

    public static OrderEditC2SPayload read(FriendlyByteBuf buf) {
        return new OrderEditC2SPayload(buf.readLong(), buf.readEnum(Action.class),
                buf.readVarInt(), buf.readVarInt(), buf.readUtf());
    }

    public static OrderEditC2SPayload of(long worksiteId, Action action, int index) {
        return new OrderEditC2SPayload(worksiteId, action, index, 0, "");
    }

    public static OrderEditC2SPayload of(long worksiteId, Action action, int index, int amount) {
        return new OrderEditC2SPayload(worksiteId, action, index, amount, "");
    }

    public static OrderEditC2SPayload of(long worksiteId, Action action, int index, String value) {
        return new OrderEditC2SPayload(worksiteId, action, index, 0, value == null ? "" : value);
    }

    //? if neoforge {
    public static final Type<OrderEditC2SPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "order_edit"));
    public static final StreamCodec<FriendlyByteBuf, OrderEditC2SPayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> payload.write(buf), OrderEditC2SPayload::read);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}
    //? if forge {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "order_edit");
    *///?}
}
