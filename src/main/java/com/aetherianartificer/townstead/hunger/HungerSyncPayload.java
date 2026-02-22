package com.aetherianartificer.townstead.hunger;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record HungerSyncPayload(
        int entityId,
        int hunger,
        int farmerTier,
        int farmerXp,
        int farmerXpToNext,
        int butcherTier,
        int butcherXp,
        int butcherXpToNext,
        int cookTier,
        int cookXp,
        int cookXpToNext
) implements CustomPacketPayload {

    public static final Type<HungerSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "hunger_sync"));

    public static final StreamCodec<FriendlyByteBuf, HungerSyncPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.entityId());
                buf.writeVarInt(payload.hunger());
                buf.writeVarInt(payload.farmerTier());
                buf.writeVarInt(payload.farmerXp());
                buf.writeVarInt(payload.farmerXpToNext());
                buf.writeVarInt(payload.butcherTier());
                buf.writeVarInt(payload.butcherXp());
                buf.writeVarInt(payload.butcherXpToNext());
                buf.writeVarInt(payload.cookTier());
                buf.writeVarInt(payload.cookXp());
                buf.writeVarInt(payload.cookXpToNext());
            },
            buf -> new HungerSyncPayload(
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt()
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
