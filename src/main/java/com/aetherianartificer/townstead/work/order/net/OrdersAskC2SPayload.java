package com.aetherianartificer.townstead.work.order.net;

import com.aetherianartificer.townstead.Townstead;

import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

/**
 * Asking a villager about their worksite's orders: first whether they can be asked, then to be
 * shown. Two questions rather than one because the dialogue has to know before it draws the option,
 * and the server has to check again before it opens anything.
 */
//? if neoforge {
public record OrdersAskC2SPayload(int villagerId, Ask ask) implements CustomPacketPayload {
//?} else {
/*public record OrdersAskC2SPayload(int villagerId, Ask ask) {
*///?}

    public enum Ask {
        /** "Could I ask them?" — answered with an {@link OrdersOfferS2CPayload}. */
        OFFER,
        /** "Ask them." — answered with the orders screen, or a refusal. */
        OPEN
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(villagerId);
        buf.writeEnum(ask);
    }

    public static OrdersAskC2SPayload read(FriendlyByteBuf buf) {
        return new OrdersAskC2SPayload(buf.readVarInt(), buf.readEnum(Ask.class));
    }

    //? if neoforge {
    public static final Type<OrdersAskC2SPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "orders_ask"));
    public static final StreamCodec<FriendlyByteBuf, OrdersAskC2SPayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> payload.write(buf), OrdersAskC2SPayload::read);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}
    //? if forge {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "orders_ask");
    *///?}
}
