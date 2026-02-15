package com.aetherianartificer.townstead.hunger;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record FarmStatusSyncPayload(int entityId, String blockedReasonId) implements CustomPacketPayload {

    public static final Type<FarmStatusSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "farm_status_sync"));

    public static final StreamCodec<FriendlyByteBuf, FarmStatusSyncPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, FarmStatusSyncPayload::entityId,
                    ByteBufCodecs.STRING_UTF8, FarmStatusSyncPayload::blockedReasonId,
                    FarmStatusSyncPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
