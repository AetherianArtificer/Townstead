package com.aetherianartificer.townstead.hunger;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ButcherStatusSyncPayload(int entityId, String blockedReasonId) implements CustomPacketPayload {

    public static final Type<ButcherStatusSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "butcher_status_sync"));

    public static final StreamCodec<FriendlyByteBuf, ButcherStatusSyncPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, ButcherStatusSyncPayload::entityId,
                    ByteBufCodecs.STRING_UTF8, ButcherStatusSyncPayload::blockedReasonId,
                    ButcherStatusSyncPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
