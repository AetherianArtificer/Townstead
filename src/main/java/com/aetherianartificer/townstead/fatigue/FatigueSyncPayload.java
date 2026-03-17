package com.aetherianartificer.townstead.fatigue;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

//? if neoforge {
public record FatigueSyncPayload(int entityId, int fatigue, boolean collapsed) implements CustomPacketPayload {
//?} else {
/*public record FatigueSyncPayload(int entityId, int fatigue, boolean collapsed) {
*///?}

    //? if neoforge {
    public static final Type<FatigueSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "fatigue_sync"));

    public static final StreamCodec<FriendlyByteBuf, FatigueSyncPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.entityId());
                buf.writeVarInt(payload.fatigue());
                buf.writeBoolean(payload.collapsed());
            },
            buf -> new FatigueSyncPayload(
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readBoolean()
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    //?}

    //? if neoforge {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "fatigue_sync");
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "fatigue_sync");
    *///?}

    //? if forge {
    /*public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeVarInt(fatigue);
        buf.writeBoolean(collapsed);
    }

    public static FatigueSyncPayload read(FriendlyByteBuf buf) {
        return new FatigueSyncPayload(buf.readVarInt(), buf.readVarInt(), buf.readBoolean());
    }
    *///?}
}
