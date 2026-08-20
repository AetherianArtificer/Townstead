package com.aetherianartificer.townstead.work.order.net;

import com.aetherianartificer.townstead.Townstead;

import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

/**
 * Whether this villager can be asked about orders. Carries the villager id so a late answer that
 * arrives after the player has walked off and started a different conversation is discarded rather
 * than offering the option on the wrong person.
 */
//? if neoforge {
public record OrdersOfferS2CPayload(int villagerId, boolean available) implements CustomPacketPayload {
//?} else {
/*public record OrdersOfferS2CPayload(int villagerId, boolean available) {
*///?}

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(villagerId);
        buf.writeBoolean(available);
    }

    public static OrdersOfferS2CPayload read(FriendlyByteBuf buf) {
        return new OrdersOfferS2CPayload(buf.readVarInt(), buf.readBoolean());
    }

    //? if neoforge {
    public static final Type<OrdersOfferS2CPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "orders_offer"));
    public static final StreamCodec<FriendlyByteBuf, OrdersOfferS2CPayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> payload.write(buf), OrdersOfferS2CPayload::read);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}
    //? if forge {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "orders_offer");
    *///?}
}
