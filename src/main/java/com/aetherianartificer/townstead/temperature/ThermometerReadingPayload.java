package com.aetherianartificer.townstead.temperature;

import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
//?}

/** Server measurement; display units are chosen by the receiving player. */
//? if neoforge {
public record ThermometerReadingPayload(float celsius, boolean coldSweat) implements CustomPacketPayload {
    public static final Type<ThermometerReadingPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("townstead", "thermometer_reading"));
    public static final StreamCodec<FriendlyByteBuf, ThermometerReadingPayload> STREAM_CODEC =
            StreamCodec.of((buf, value) -> value.write(buf), ThermometerReadingPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
//?} else {
/*public record ThermometerReadingPayload(float celsius, boolean coldSweat) {
*///?}
    public void write(FriendlyByteBuf buf) {
        buf.writeFloat(celsius);
        buf.writeBoolean(coldSweat);
    }

    public static ThermometerReadingPayload read(FriendlyByteBuf buf) {
        return new ThermometerReadingPayload(buf.readFloat(), buf.readBoolean());
    }
}
