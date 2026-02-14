package com.aetherianartificer.townstead.hunger;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record HungerSyncPayload(int entityId, int hunger) implements CustomPacketPayload {

    public static final Type<HungerSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "hunger_sync"));

    public static final StreamCodec<FriendlyByteBuf, HungerSyncPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, HungerSyncPayload::entityId,
                    ByteBufCodecs.VAR_INT, HungerSyncPayload::hunger,
                    HungerSyncPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
