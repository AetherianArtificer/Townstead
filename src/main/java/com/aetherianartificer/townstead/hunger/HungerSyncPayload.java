package com.aetherianartificer.townstead.hunger;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record HungerSyncPayload(int entityId, int hunger, int farmerTier, int farmerXp, int farmerXpToNext) implements CustomPacketPayload {

    public static final Type<HungerSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "hunger_sync"));

    public static final StreamCodec<FriendlyByteBuf, HungerSyncPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, HungerSyncPayload::entityId,
                    ByteBufCodecs.VAR_INT, HungerSyncPayload::hunger,
                    ByteBufCodecs.VAR_INT, HungerSyncPayload::farmerTier,
                    ByteBufCodecs.VAR_INT, HungerSyncPayload::farmerXp,
                    ByteBufCodecs.VAR_INT, HungerSyncPayload::farmerXpToNext,
                    HungerSyncPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
