package com.aetherianartificer.townstead.thirst;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

//? if neoforge {
public record ThirstSyncPayload(int entityId, int thirst, int quenched) implements CustomPacketPayload {
//?} else {
/*public record ThirstSyncPayload(int entityId, int thirst, int quenched) {
*///?}

    //? if neoforge {
    public static final Type<ThirstSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "thirst_sync"));

    public static final StreamCodec<FriendlyByteBuf, ThirstSyncPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.entityId());
                buf.writeVarInt(payload.thirst());
                buf.writeVarInt(payload.quenched());
            },
            buf -> new ThirstSyncPayload(
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt()
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    //?}

    //? if neoforge {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "thirst_sync");
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "thirst_sync");
    *///?}

    //? if forge {
    /*public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeVarInt(thirst);
        buf.writeVarInt(quenched);
    }

    public static ThirstSyncPayload read(FriendlyByteBuf buf) {
        return new ThirstSyncPayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }
    *///?}
}
