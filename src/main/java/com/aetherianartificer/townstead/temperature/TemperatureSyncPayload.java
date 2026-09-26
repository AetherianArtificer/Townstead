package com.aetherianartificer.townstead.temperature;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

/** Body and ambient temperature in Celsius tenths plus a flag byte: bit 0 wet, bit 1 seeking relief, bits 2–4 comfort, bits 5–7 core tier. */
//? if neoforge {
public record TemperatureSyncPayload(int entityId, int bodyTenths, int ambientTenths, int flags) implements CustomPacketPayload {
//?} else {
/*public record TemperatureSyncPayload(int entityId, int bodyTenths, int ambientTenths, int flags) {
*///?}

    public static final int FLAG_WET = 1;
    public static final int FLAG_SEEKING_RELIEF = 2;
    private static final int TIER_SHIFT = 2;

    public static int flags(boolean wet, boolean seekingRelief, int tierOrdinal) {
        return (wet ? FLAG_WET : 0) | (seekingRelief ? FLAG_SEEKING_RELIEF : 0) | ((tierOrdinal & 7) << TIER_SHIFT);
    }

    public static int tier(int flags) {
        return (flags >> TIER_SHIFT) & 7;
    }

    //? if neoforge {
    public static final Type<TemperatureSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "temperature_sync"));

    public static final StreamCodec<FriendlyByteBuf, TemperatureSyncPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.entityId());
                buf.writeInt(payload.bodyTenths());
                buf.writeInt(payload.ambientTenths());
                buf.writeVarInt(payload.flags());
            },
            buf -> new TemperatureSyncPayload(buf.readVarInt(), buf.readInt(), buf.readInt(), buf.readVarInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    //?}

    //? if neoforge {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "temperature_sync");
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "temperature_sync");
    *///?}

    //? if forge {
    /*public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeInt(bodyTenths);
        buf.writeInt(ambientTenths);
        buf.writeVarInt(flags);
    }

    public static TemperatureSyncPayload read(FriendlyByteBuf buf) {
        return new TemperatureSyncPayload(buf.readVarInt(), buf.readInt(), buf.readInt(), buf.readVarInt());
    }
    *///?}
}
