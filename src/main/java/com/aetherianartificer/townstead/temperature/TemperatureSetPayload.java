package com.aetherianartificer.townstead.temperature;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

/** Client to server: set the body temperature in Celsius tenths, or {@code -1} to request a fresh sync. */
//? if neoforge {
public record TemperatureSetPayload(int entityId, int bodyTenths) implements CustomPacketPayload {
//?} else {
/*public record TemperatureSetPayload(int entityId, int bodyTenths) {
*///?}

    //? if neoforge {
    public static final Type<TemperatureSetPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "temperature_set"));

    public static final StreamCodec<FriendlyByteBuf, TemperatureSetPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, TemperatureSetPayload::entityId,
                    ByteBufCodecs.INT, TemperatureSetPayload::bodyTenths,
                    TemperatureSetPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    //?}

    //? if neoforge {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "temperature_set");
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "temperature_set");
    *///?}

    //? if forge {
    /*public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeInt(bodyTenths);
    }

    public static TemperatureSetPayload read(FriendlyByteBuf buf) {
        return new TemperatureSetPayload(buf.readVarInt(), buf.readInt());
    }
    *///?}
}
